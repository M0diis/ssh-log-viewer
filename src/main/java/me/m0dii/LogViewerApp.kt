package me.m0dii

import com.jcraft.jsch.JSch
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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
    private val wrapTextCheckbox = JCheckBox("Wrap text")

    private val searchField = JTextField("", 15)
    private val fetchButton = JButton("Fetch Logs")
    private val filterField = JTextField("", 15)
    private val stopButton = JButton("Stop Fetching")
    private val clearButton = JButton("Clear")
    private val hitCountLabel = JLabel("Hits: 0")
    private val statusLabel = JLabel("Status: Idle")
    private val tabbedPane = JTabbedPane()

    private val searchReplaceField = JTextField("", 15)
    private val replaceField = JTextField("", 15)
    private val replaceButton = JButton("Replace")

    private var debounceJob: Job? = null
    private val stopFetching = AtomicBoolean(false)
    private val activeSessions = mutableListOf<com.jcraft.jsch.Session>()

    init {
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

        // Wrap Length and Search and Replace Panel
        val middleLeftPanel = JPanel(GridBagLayout())
        val mlc = GridBagConstraints()
        mlc.insets = Insets(5, 5, 5, 5)
        mlc.fill = GridBagConstraints.HORIZONTAL

        mlc.gridx = 0
        mlc.gridy = 1
        middleLeftPanel.add(wrapTextCheckbox, mlc)

        mlc.gridx = 0
        mlc.gridy = 2
        middleLeftPanel.add(JLabel("Search:").apply { font = labelFont }, mlc)
        mlc.gridx = 0
        mlc.gridy = 3
        middleLeftPanel.add(searchReplaceField, mlc)

        mlc.gridx = 0
        mlc.gridy = 4
        middleLeftPanel.add(JLabel("Replace:").apply { font = labelFont }, mlc)
        mlc.gridx = 0
        mlc.gridy = 5
        middleLeftPanel.add(replaceField, mlc)

        mlc.gridx = 0
        mlc.gridy = 6
        middleLeftPanel.add(replaceButton, mlc)

        // Hosts Panel
        val middleRightPanel = JPanel(GridBagLayout())
        val mrc = GridBagConstraints()
        mrc.insets = Insets(5, 5, 5, 5)
        mrc.fill = GridBagConstraints.HORIZONTAL

        mrc.gridx = 0
        mrc.gridy = 0
        middleRightPanel.add(JLabel("Hosts:").apply { font = labelFont }, mrc)
        mrc.gridx = 0
        mrc.gridy = 1
        mrc.gridwidth = 2
        mrc.gridheight = 2
        val hostScrollPane = JScrollPane(hostList)
        hostScrollPane.preferredSize = Dimension(200, 205)
        middleRightPanel.add(hostScrollPane, mrc)
        mrc.gridwidth = 1
        mrc.gridheight = 1
        mrc.gridx = 0
        mrc.gridy = 3
        middleRightPanel.add(addHostButton, mrc)
        mrc.gridx = 1
        middleRightPanel.add(removeHostButton, mrc)

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
        inputPanel.add(middleLeftPanel, c)
        c.gridx = 2
        inputPanel.add(middleRightPanel, c)
        c.gridx = 3
        inputPanel.add(rightPanel, c)

        val buttonPanel = JPanel(FlowLayout())
        buttonPanel.add(fetchButton)
        buttonPanel.add(stopButton)
        buttonPanel.add(clearButton)
        buttonPanel.add(hitCountLabel)
        buttonPanel.add(statusLabel)

        add(inputPanel, BorderLayout.NORTH)
        add(buttonPanel, BorderLayout.SOUTH)
        add(tabbedPane, BorderLayout.CENTER)

        fetchButton.addActionListener {
            fetchLogs()
        }

        stopButton.addActionListener {
            stopFetching()
        }

        clearButton.addActionListener {
            tabbedPane.removeAll()
            hitCountLabel.text = "Hits: 0"
        }

        tabbedPane.addChangeListener {
            filterLogs(filterField.text)
        }

        tabbedPane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    val tabIndex = tabbedPane.indexAtLocation(e.x, e.y)
                    if (tabIndex != -1) {
                        tabbedPane.remove(tabIndex)
                    }
                }
            }
        })

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

        replaceButton.addActionListener {
            replaceText()
        }
    }

    private fun fetchLogs() {
        if (hostListModel.isEmpty || pathListModel.isEmpty) {
            JOptionPane.showMessageDialog(this, "Please add at least one host and log path.")
            return
        }

        stopFetching.set(false) // Reset the stopFetching flag

        fetchButton.isEnabled = false
        hitCountLabel.text = "Hits: 0"
        statusLabel.text = "Status: Fetching..."

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
                statusLabel.text = "Status: Fetching complete"
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
            if (stopFetching.get()) {
                withContext(Dispatchers.Main) {
                    statusLabel.text = "Status: Fetching stopped"
                }
                return
            }

            val logArea = JTextArea()
                .apply { font = Font("Monospaced", Font.PLAIN, 12) }

            logArea.isEditable = true
            logArea.lineWrap = wrapTextCheckbox.isSelected
            logArea.wrapStyleWord = wrapTextCheckbox.isSelected
            val scrollPane = JScrollPane(logArea)
            withContext(Dispatchers.Main) {
                tabbedPane.addTab(host, scrollPane)
            }

            for (path in paths) {
                fetchLogsFromServer(host, user, password, privateKey, path, searchQuery, logArea)
            }
        }
    }

    private suspend fun fetchLogsFromServer(
        host: String,
        user: String,
        password: String,
        privateKey: File?,
        logPath: String,
        searchQuery: String,
        logArea: JTextArea
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
        if (filter.isNotEmpty()) {
            tabbedPane.components.forEach { pane ->
                val scrollPane = pane as JScrollPane
                val logArea = scrollPane.viewport.view as JTextArea
                val logs = logArea.text
                val filteredLogs = logs.lines()
                    .filter { it.contains(filter, ignoreCase = true) }
                    .joinToString("\n")
                logArea.text = filteredLogs
            }
        } else {
            tabbedPane.components.forEach {
                val scrollPane = it as JScrollPane
                val logArea = scrollPane.viewport.view as JTextArea
                val logs = logArea.text
                logArea.text = logs
            }
        }

        val hitCount = tabbedPane.components.sumOf {
            val scrollPane = it as JScrollPane
            val logArea = scrollPane.viewport.view as JTextArea
            logArea.text.lines().size
        }

        hitCountLabel.text = "Hits: $hitCount"

        tabbedPane.repaint()
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

    private fun replaceText() {
        val searchText = searchReplaceField.text
        val replaceText = replaceField.text

        tabbedPane.components.forEach {
            val scrollPane = it as JScrollPane
            val logArea = scrollPane.viewport.view as JTextArea
            val logs = logArea.text
            val replacedLogs = logs.replace(searchText, replaceText)
            logArea.text = replacedLogs
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