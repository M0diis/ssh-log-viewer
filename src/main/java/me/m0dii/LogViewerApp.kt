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

    private val fetchButton = JButton("Fetch Logs")
    private val filterField = JTextField("", 15)
    private val stopButton = JButton("Stop Fetching")
    private val clearButton = JButton("Clear")
    private val hitCountLabel = JLabel("Hits: 0")
    private val statusLabel = JLabel("Status: Idle")
    private val tabbedPane = JTabbedPane()

    private val linesAroundLabel = JLabel("Lines Around")
    private val linesAroundModel = SpinnerNumberModel(1, 0, 100, 1)
    private val linesAroundField = JSpinner(linesAroundModel)
    private val wrapTextLabel = JLabel("Wrap Text")
    private val wrapTextCheckbox = JCheckBox()
    private val searchReplaceField = JTextField("", 15)
    private val replaceField = JTextField("", 15)
    private val replaceButton = JButton("Replace")

    private val searchQueryListModel = DefaultListModel<String>()
    private val searchQueryList = JList(searchQueryListModel)
    private val addSearchQueryButton = JButton("Add Search Query")
    private val removeSearchQueryButton = JButton("Remove Search Query")
    private val moveUpSearchQueryButton = JButton("Move Up")
    private val moveDownSearchQueryButton = JButton("Move Down")

    private var debounceJob: Job? = null
    private val stopFetching = AtomicBoolean(false)
    private val activeSessions = mutableListOf<com.jcraft.jsch.Session>()

    private val textAreaList = mutableListOf<JTextArea>()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        setSize(1400, 800)
        layout = BorderLayout()

        val inputPanel = JPanel(GridBagLayout())
        val c = GridBagConstraints()
        c.insets = Insets(3, 3, 3, 3)
        c.fill = GridBagConstraints.HORIZONTAL
        val labelFont = Font("Arial", Font.BOLD, 12)

        // User, Password, Key File, Search Query, Filter Output Panel
        val leftPanel = JPanel(GridBagLayout())
        val lc = GridBagConstraints()
        lc.insets = Insets(3, 3, 3, 3)
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

//        lc.gridx = 0
//        lc.gridy = 6
//        leftPanel.add(JLabel("Search Query:").apply { font = labelFont }, lc)
//        lc.gridx = 0
//        lc.gridy = 7
//        leftPanel.add(searchField, lc)

        lc.gridx = 0
        lc.gridy = 8
        leftPanel.add(JLabel("Filter Output:").apply { font = labelFont }, lc)
        lc.gridx = 0
        lc.gridy = 9
        leftPanel.add(filterField, lc)

        // Wrap Length and Search and Replace Panel
        val middleLeftPanel = JPanel(GridBagLayout())
        val mlc = GridBagConstraints()
        mlc.insets = Insets(3, 3, 3, 3)
        mlc.fill = GridBagConstraints.HORIZONTAL

        mlc.gridx = 0; mlc.gridy = 1
        middleLeftPanel.add(wrapTextLabel, mlc)

        mlc.gridx = 0; mlc.gridy = 2
        middleLeftPanel.add(wrapTextCheckbox, mlc)

        mlc.gridx = 0; mlc.gridy = 3
        middleLeftPanel.add(linesAroundLabel, mlc)

        mlc.gridx = 0; mlc.gridy = 4
        middleLeftPanel.add(linesAroundField, mlc)

        mlc.gridx = 0; mlc.gridy = 5
        middleLeftPanel.add(JLabel("Search:").apply { font = labelFont }, mlc)

        mlc.gridx = 0; mlc.gridy = 6
        middleLeftPanel.add(searchReplaceField, mlc)

        mlc.gridx = 0; mlc.gridy = 7
        middleLeftPanel.add(JLabel("Replace:").apply { font = labelFont }, mlc)

        mlc.gridx = 0; mlc.gridy = 8
        middleLeftPanel.add(replaceField, mlc)

        mlc.gridx = 0; mlc.gridy = 9
        middleLeftPanel.add(replaceButton, mlc)

        // Hosts Panel
        val middleRightPanel = JPanel(GridBagLayout())
        val mrc = GridBagConstraints()
        mrc.insets = Insets(3, 3, 3, 3)
        mrc.fill = GridBagConstraints.HORIZONTAL

        mrc.gridx = 0; mrc.gridy = 0
        middleRightPanel.add(JLabel("Hosts:").apply { font = labelFont }, mrc)

        mrc.gridx = 0; mrc.gridy = 1
        mrc.gridwidth = 2
        mrc.gridheight = 2
        val hostScrollPane = JScrollPane(hostList)
        hostScrollPane.preferredSize = Dimension(200, 205)
        middleRightPanel.add(hostScrollPane, mrc)

        mrc.gridwidth = 1
        mrc.gridheight = 1
        mrc.gridx = 0; mrc.gridy = 3
        middleRightPanel.add(addHostButton, mrc)
        mrc.gridx = 1
        middleRightPanel.add(removeHostButton, mrc)

        // Log File Paths Panel
        val rightPanel = JPanel(GridBagLayout())
        val rc = GridBagConstraints()
        rc.insets = Insets(3, 3, 3, 3)
        rc.fill = GridBagConstraints.HORIZONTAL

        rc.gridx = 0; rc.gridy = 0
        rightPanel.add(JLabel("Log File Paths:").apply { font = labelFont }, rc)
        rc.gridx = 0; rc.gridy = 1
        rc.gridwidth = 2
        rc.gridheight = 2
        val pathScrollPane = JScrollPane(pathList)
        pathScrollPane.preferredSize = Dimension(310, 205)
        rightPanel.add(pathScrollPane, rc)
        rc.gridwidth = 1
        rc.gridheight = 1
        rc.gridx = 0; rc.gridy = 3
        rightPanel.add(addPathButton, rc)
        rc.gridx = 1
        rightPanel.add(removePathButton, rc)

        // Search query panel
        val searchQueryPanel = JPanel(GridBagLayout())
        val sqc = GridBagConstraints()
        sqc.insets = Insets(3, 3, 3, 3)
        sqc.fill = GridBagConstraints.HORIZONTAL

        sqc.gridx = 0; sqc.gridy = 0
        searchQueryPanel.add(JLabel("Search Queries:").apply { font = labelFont }, sqc)
        sqc.gridx = 0; sqc.gridy = 1
        sqc.gridwidth = 2
        sqc.gridheight = 2
        val searchQueryScrollPane = JScrollPane(searchQueryList)
        searchQueryScrollPane.preferredSize = Dimension(200, 175)
        searchQueryPanel.add(searchQueryScrollPane, sqc)

        sqc.gridwidth = 1
        sqc.gridheight = 1
        sqc.gridx = 0; sqc.gridy = 3
        searchQueryPanel.add(addSearchQueryButton, sqc)
        sqc.gridx = 1
        searchQueryPanel.add(removeSearchQueryButton, sqc)
        sqc.gridx = 0; sqc.gridy = 4
        searchQueryPanel.add(moveUpSearchQueryButton, sqc)
        sqc.gridx = 1
        searchQueryPanel.add(moveDownSearchQueryButton, sqc)

        moveUpSearchQueryButton.addActionListener {
            val selectedIndex = searchQueryList.selectedIndex
            if (selectedIndex > 0) {
                val element = searchQueryListModel.remove(selectedIndex)
                searchQueryListModel.add(selectedIndex - 1, element)
                searchQueryList.selectedIndex = selectedIndex - 1
            }
        }

        moveDownSearchQueryButton.addActionListener {
            val selectedIndex = searchQueryList.selectedIndex
            if (selectedIndex < searchQueryListModel.size - 1) {
                val element = searchQueryListModel.remove(selectedIndex)
                searchQueryListModel.add(selectedIndex + 1, element)
                searchQueryList.selectedIndex = selectedIndex + 1
            }
        }

        // Add panels to inputPanel
        c.gridx = 0; c.gridy = 0
        inputPanel.add(leftPanel, c)
        c.gridx = 1
        inputPanel.add(middleLeftPanel, c)
        c.gridx = 2
        inputPanel.add(middleRightPanel, c)
        c.gridx = 3
        inputPanel.add(rightPanel, c)
        c.gridx = 4
        inputPanel.add(searchQueryPanel, c)

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
                        textAreaList.removeAt(tabIndex)
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

        wrapTextCheckbox.addActionListener {
            textAreaList.forEach { it.lineWrap = wrapTextCheckbox.isSelected }
        }

        addSearchQueryButton.addActionListener {
            val query = JOptionPane.showInputDialog(this, "Enter Search Query:")
            if (query != null && query.isNotBlank()) {
                searchQueryListModel.addElement(query.trim())
            }
        }

        removeSearchQueryButton.addActionListener {
            val selectedQuery = searchQueryList.selectedValue
            if (selectedQuery != null) {
                searchQueryListModel.removeElement(selectedQuery)
            }
        }

        hostList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val index = hostList.locationToIndex(e.point)
                    if (index != -1) {
                        val currentHost = hostListModel.getElementAt(index)
                        val newHost = JOptionPane.showInputDialog(this@LogViewerApp, "Edit Host:", currentHost)
                        if (newHost != null && newHost.isNotBlank()) {
                            hostListModel.setElementAt(newHost.trim(), index)
                        }
                    }
                }
            }
        })

        pathList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val index = pathList.locationToIndex(e.point)
                    if (index != -1) {
                        val currentPath = pathListModel.getElementAt(index)
                        val newPath = JOptionPane.showInputDialog(this@LogViewerApp, "Edit Path:", currentPath)
                        if (newPath != null && newPath.isNotBlank()) {
                            pathListModel.setElementAt(newPath.trim(), index)
                        }
                    }
                }
            }
        })

        searchQueryList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val index = searchQueryList.locationToIndex(e.point)
                    if (index != -1) {
                        val currentQuery = searchQueryListModel.getElementAt(index)
                        val newQuery =
                            JOptionPane.showInputDialog(this@LogViewerApp, "Edit Search Query:", currentQuery)
                        if (newQuery != null && newQuery.isNotBlank()) {
                            searchQueryListModel.setElementAt(newQuery.trim(), index)
                        }
                    }
                }
            }
        })
    }

    private fun fetchLogs() {
        if (hostListModel.isEmpty || pathListModel.isEmpty || searchQueryListModel.isEmpty) {
            JOptionPane.showMessageDialog(this, "Please add at least one host, log path, and search query.")
            return
        }

        stopFetching.set(false)

        fetchButton.isEnabled = false
        hitCountLabel.text = "Hits: 0"
        statusLabel.text = "Status: Fetching..."

        CoroutineScope(Dispatchers.IO).launch {
            val hosts = hostListModel.elements().toList()
            val paths = pathListModel.elements().toList()
            val searchQueries = searchQueryListModel.elements().toList()

            fetchLogsFromServers(
                hosts,
                userField.text,
                String(passField.password),
                privateKeyFile,
                paths,
                searchQueries
            )
        }
    }

    private suspend fun fetchLogsFromServers(
        hosts: List<String>,
        user: String,
        password: String,
        privateKey: File?,
        paths: List<String>,
        searchQueries: List<String>
    ) {
        coroutineScope {
            val jobs = hosts.map { host ->
                async {
                    if (stopFetching.get()) return@async

                    val logArea = JTextArea().apply { font = Font("Monospaced", Font.PLAIN, 12) }
                    logArea.isEditable = true
                    logArea.lineWrap = wrapTextCheckbox.isSelected
                    logArea.wrapStyleWord = wrapTextCheckbox.isSelected
                    val scrollPane = JScrollPane(logArea)
                    withContext(Dispatchers.Main) {
                        tabbedPane.addTab(host, scrollPane)
                    }

                    textAreaList.add(logArea)

                    fetchLogsFromServer(host, user, password, privateKey, paths, searchQueries, logArea)
                }
            }
            jobs.awaitAll()
        }

        withContext(Dispatchers.Main) {
            fetchButton.isEnabled = true
            statusLabel.text = "Status: Fetching complete"
        }
    }

    private suspend fun fetchLogsFromServer(
        host: String,
        user: String,
        password: String,
        privateKey: File?,
        paths: List<String>,
        searchQueries: List<String>,
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

        var args = "-H -i"
        if (linesAroundModel.value != 0) {
            args += " -C ${linesAroundModel.value}"
        }

        withContext(Dispatchers.Main) {
            logArea.text = ""
        }

        for (path in paths) {
            val command = if (searchQueries.size == 1) {
                "zgrep $args '${searchQueries[0]}' $path"
            } else {
                "zgrep $args '${searchQueries[0]}' $path | " + searchQueries.drop(1)
                    .joinToString(" | ") { "grep -i '$it'" }
            }

            println("Running command: $command on $host")
            val channel = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
            channel.setCommand(command)

            val inputStream = channel.inputStream
            channel.connect()

            val reader = BufferedReader(InputStreamReader(inputStream), 8192 * 4)
            var line: String?
            var hitCount = 0
            var lineNumber = 1

            withContext(Dispatchers.Main) {
                logArea.append("Executing command on host $host:\n")
                logArea.append("$ ssh $user@$host $command\n\n")
            }

            while (withContext(Dispatchers.IO) {
                    reader.readLine()
                }.also { line = it } != null) {
                if (stopFetching.get()) break
                if (!line?.isBlank()!!) {
                    hitCount++
                }
                withContext(Dispatchers.Main) {
                    val caretPosition = logArea.caretPosition
                    logArea.append("%4d. %s\n".format(lineNumber++, line))
                    logArea.caretPosition = caretPosition
                    hitCountLabel.text = "Hits: $hitCount"
                }
            }
            channel.disconnect()
        }

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
        statusLabel.text = "Status: Fetching cancelled"
        fetchButton.isEnabled = true
    }
}

fun main() {
    SwingUtilities.invokeLater {
        LogViewerApp().isVisible = true
    }
}