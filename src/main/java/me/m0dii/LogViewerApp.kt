package me.m0dii

import com.jcraft.jsch.JSch
import kotlinx.coroutines.*
import java.awt.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*

class LogViewerApp : JFrame("SSH Log Viewer") {
    private val hostListModel = DefaultListModel<String>()
    private val hostList = JList(hostListModel)
    private val addHostButton = JButton("Add Host")
    private val removeHostButton = JButton("Remove Host")

    private val userField = JTextField("username", 15)
    private val passField = JPasswordField("password", 15)
    private val keyFileButton = JButton("Select Private Key")
    private var privateKeyFile: File? = null
    private val pathListModel = DefaultListModel<String>()
    private val pathList = JList(pathListModel)
    private val addPathButton = JButton("Add Path")
    private val removePathButton = JButton("Remove Path")

    private val searchField = JTextField("", 15)
    private val fetchButton = JButton("Fetch Logs")
    private val logArea = JTextArea()
    private val filterField = JTextField("", 15)
    private val stopButton = JButton("Stop Fetching")
    private val clearButton = JButton("Clear")
    private val hitCountLabel = JLabel("Hits: 0")

    private var debounceJob: Job? = null
    private val logs = StringBuilder()
    private val stopFetching = AtomicBoolean(false)
    private val activeSessions = mutableListOf<com.jcraft.jsch.Session>()

    init {
        JSch.setConfig("server_host_key", "ssh-ed25519");

        pathListModel.addElement("/home/username/*.log")
        pathListModel.addElement("/var/log/*.gz")

        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(1000, 800)
        layout = BorderLayout()

        val inputPanel = JPanel(GridBagLayout())
        val c = GridBagConstraints()
        c.insets = Insets(5, 5, 5, 5)
        c.fill = GridBagConstraints.HORIZONTAL
        val labelFont = Font("Arial", Font.BOLD, 12)

        // User, Password, Key File, Search Query, Filter Output Panel
        val leftPanel = JPanel(GridBagLayout())
        val lc = GridBagConstraints()
        lc.insets = Insets(5, 5, 5, 5)
        lc.fill = GridBagConstraints.HORIZONTAL

        lc.gridx = 0
        lc.gridy = 0
        leftPanel.add(JLabel("User:").apply { font = labelFont }, lc)
        lc.gridx = 0
        lc.gridy = 1
        leftPanel.add(userField, lc)

        lc.gridx = 0
        lc.gridy = 2
        leftPanel.add(JLabel("Password:").apply { font = labelFont }, lc)
        lc.gridx = 0
        lc.gridy = 3
        leftPanel.add(passField, lc)

        lc.gridx = 0
        lc.gridy = 4
        leftPanel.add(JLabel("Private Key:").apply { font = labelFont }, lc)
        lc.gridx = 0
        lc.gridy = 5
        leftPanel.add(keyFileButton, lc)

        lc.gridx = 0
        lc.gridy = 6
        leftPanel.add(JLabel("Search Query:").apply { font = labelFont }, lc)
        lc.gridx = 0
        lc.gridy = 7
        leftPanel.add(searchField, lc)

        lc.gridx = 0
        lc.gridy = 8
        leftPanel.add(JLabel("Filter Output:").apply { font = labelFont }, lc)
        lc.gridx = 0
        lc.gridy = 9
        leftPanel.add(filterField, lc)

        // Hosts Panel
        val middlePanel = JPanel(GridBagLayout())
        val mc = GridBagConstraints()
        mc.insets = Insets(5, 5, 5, 5)
        mc.fill = GridBagConstraints.HORIZONTAL

        mc.gridx = 0
        mc.gridy = 0
        middlePanel.add(JLabel("Hosts:").apply { font = labelFont }, mc)
        mc.gridx = 0
        mc.gridy = 1
        mc.gridwidth = 2
        mc.gridheight = 2
        val hostScrollPane = JScrollPane(hostList)
        hostScrollPane.preferredSize = Dimension(200, 205)
        middlePanel.add(hostScrollPane, mc)
        mc.gridwidth = 1
        mc.gridheight = 1
        mc.gridx = 0
        mc.gridy = 3
        middlePanel.add(addHostButton, mc)
        mc.gridx = 1
        middlePanel.add(removeHostButton, mc)

        // Log File Paths Panel
        val rightPanel = JPanel(GridBagLayout())
        val rc = GridBagConstraints()
        rc.insets = Insets(5, 5, 5, 5)
        rc.fill = GridBagConstraints.HORIZONTAL

        rc.gridx = 0
        rc.gridy = 0
        rightPanel.add(JLabel("Log File Paths:").apply { font = labelFont }, rc)
        rc.gridx = 0
        rc.gridy = 1
        rc.gridwidth = 2
        rc.gridheight = 2
        val pathScrollPane = JScrollPane(pathList)
        pathScrollPane.preferredSize = Dimension(200, 205)
        rightPanel.add(pathScrollPane, rc)
        rc.gridwidth = 1
        rc.gridheight = 1
        rc.gridx = 0
        rc.gridy = 3
        rightPanel.add(addPathButton, rc)
        rc.gridx = 1
        rightPanel.add(removePathButton, rc)

        // Add panels to inputPanel
        c.gridx = 0
        c.gridy = 0
        inputPanel.add(leftPanel, c)
        c.gridx = 1
        inputPanel.add(middlePanel, c)
        c.gridx = 2
        inputPanel.add(rightPanel, c)

        val buttonPanel = JPanel(FlowLayout())
        buttonPanel.add(fetchButton)
        buttonPanel.add(stopButton)
        buttonPanel.add(clearButton)
        buttonPanel.add(hitCountLabel)

        add(inputPanel, BorderLayout.NORTH)
        add(buttonPanel, BorderLayout.SOUTH)

        logArea.isEditable = false
        add(JScrollPane(logArea), BorderLayout.CENTER)

        fetchButton.addActionListener {
            fetchLogs()
        }

        stopButton.addActionListener {
            stopFetching()
        }

        clearButton.addActionListener {
            logArea.text = ""
            hitCountLabel.text = "Hits: 0"
            logs.clear()
        }

        addHostButton.addActionListener {
            val host = JOptionPane.showInputDialog(this, "Enter Host:")
            if (host != null && host.isNotBlank()) {
                hostListModel.addElement(host.trim())
            }
        }

        removeHostButton.addActionListener {
            val selectedHost = hostList.selectedValue
            if (selectedHost != null) {
                hostListModel.removeElement(selectedHost)
            }
        }

        addPathButton.addActionListener {
            val path = JOptionPane.showInputDialog(this, "Enter Log File Path:")
            if (path != null && path.isNotBlank()) {
                pathListModel.addElement(path.trim())
            }
        }

        removePathButton.addActionListener {
            val selectedPath = pathList.selectedValue
            if (selectedPath != null) {
                pathListModel.removeElement(selectedPath)
            }
        }

        filterField.addCaretListener {
            debounceFilter()
        }

        keyFileButton.addActionListener {
            val fileChooser = JFileChooser()
            fileChooser.fileSelectionMode = JFileChooser.FILES_ONLY
            val result = fileChooser.showOpenDialog(this)
            if (result == JFileChooser.APPROVE_OPTION) {
                privateKeyFile = fileChooser.selectedFile
            }
        }
    }

    private fun fetchLogs() {
        if (hostListModel.isEmpty || pathListModel.isEmpty) {
            logArea.text = "Please add at least one host and log path."
            return
        }

        stopFetching.set(false) // Reset the stopFetching flag

        fetchButton.isEnabled = false
        logArea.text = "Fetching logs... \n\n"
        hitCountLabel.text = "Hits: 0"

        CoroutineScope(Dispatchers.IO).launch {
            val hosts = hostListModel.elements().toList()
            val paths = pathListModel.elements().toList()

            fetchLogsFromServers(
                hosts,
                userField.text,
                String(passField.password),
                privateKeyFile,
                paths,
                searchField.text
            )

            withContext(Dispatchers.Main) {
                fetchButton.isEnabled = true
            }
        }
    }
    private suspend fun fetchLogsFromServers(
        hosts: List<String>,
        user: String,
        password: String,
        privateKey: File?,
        paths: List<String>,
        searchQuery: String
    ) {
        for (host in hosts) {
            for (path in paths) {
                if (stopFetching.get()) {
                    return
                }

                fetchLogsFromServer(host, user, password, privateKey, path, searchQuery)
            }
        }
    }

    private suspend fun fetchLogsFromServer(
        host: String,
        user: String,
        password: String,
        privateKey: File?,
        logPath: String,
        searchQuery: String
    ) {
        val jsch = JSch()
        if (privateKey != null) {
            jsch.addIdentity(privateKey.absolutePath)
        }
        val session = jsch.getSession(user, host)
        if (privateKey == null) {
            session.setPassword(password)
        }
        session.setConfig("StrictHostKeyChecking", "no")

        try {
            session.connect()
            activeSessions.add(session)
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                logArea.append("Error connecting to $host: ${e.message}\n")
            }
            throw e
        }

        val command = "zgrep -H \"$searchQuery\" $logPath"
        val channel = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
        channel.setCommand(command)

        val inputStream = channel.inputStream
        channel.connect()

        val reader = BufferedReader(InputStreamReader(inputStream), 8192 * 4) // Increase buffer size
        var line: String?
        var hitCount = 0
        var lineNumber = 1

        while (withContext(Dispatchers.IO) {
                reader.readLine()
            }.also { line = it } != null) {
            if (stopFetching.get()) break
            hitCount++
            withContext(Dispatchers.Main) {
                val caretPosition = logArea.caretPosition
                logArea.append("%4d. %s\n".format(lineNumber++, line))
                logArea.caretPosition = caretPosition
                hitCountLabel.text = "Hits: $hitCount"
            }
        }

        channel.disconnect()
        session.disconnect()
        activeSessions.remove(session)
    }

    private fun filterLogs(filter: String) {
        val filteredText = if (filter.isNotEmpty()) {
            logs.lines().filter { it.contains(filter, ignoreCase = true) }.joinToString("\n")
        } else {
            logs.toString()
        }

        if (filteredText.isEmpty()) {
            logArea.text = "No results found."
            return
        }

        logArea.text = appendLineNumbers(filteredText)
    }

    private fun appendLineNumbers(logs: String): String {
        return logs.lines()
            .filter { it.isNotEmpty() }
            .mapIndexed { index, line -> "%4d. %s".format(index + 1, line) }
            .joinToString("\n")
    }

    private fun debounceFilter() {
        debounceJob?.cancel()

        debounceJob = CoroutineScope(Dispatchers.Main).launch {
            delay(300)
            filterLogs(filterField.text)
        }
    }

    private fun stopFetching() {
        stopFetching.set(true)
        activeSessions.forEach { it.disconnect() }
        activeSessions.clear()
    }
}

fun main() {
    SwingUtilities.invokeLater {
        LogViewerApp().isVisible = true
    }
}