package com.br.minehub.main;

import com.br.minehub.main.editor.YamlEditor;
import com.br.minehub.main.model.RemoteFileItem;
import com.br.minehub.main.service.SftpService;
import com.br.minehub.main.service.pterodactyl.PterodactylService;
import com.br.minehub.main.service.pterodactyl.model.PteroServer;
import com.br.minehub.main.service.pterodactyl.ws.PteroConsoleWebSocket;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    private final PterodactylService pteroService = new PterodactylService();
    private final SftpService sftpService = new SftpService();
    private PteroConsoleWebSocket consoleSocket;
    private String currentPath = "/";
    private double xOffset;
    private double yOffset;

    @FXML
    private HBox titleBar;

    @FXML
    private ComboBox<PteroServer> serverSelector;

    @FXML
    private ProgressBar uploadProgress;

    @FXML
    private StackPane contentArea;

    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        setupWindowDrag();
        serverSelector.setDisable(true);

        serverSelector.setOnAction(e -> {
            PteroServer server = serverSelector.getValue();

            if (server == null) {
                return;
            }

            pteroService.selectServer(server.getIdentifier());
            currentPath = "/";

            setStatus("Servidor selecionado: " + server.getName());

            showFiles();
        });

        showFiles();
        setStatus("MineHub iniciado");
    }

    private void downloadRemoteItem(RemoteFileItem item) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Escolha onde salvar");

        java.io.File selectedDir = chooser.showDialog(contentArea.getScene().getWindow());

        if (selectedDir == null) {
            return;
        }

        String remotePath = resolvePath(item.getName());

        setStatus("Baixando " + item.getName() + "...");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                sftpService.downloadPath(remotePath, selectedDir.toPath());
                return null;
            }
        };

        task.setOnSucceeded(e ->
                setStatus("Download concluído: " + item.getName())
        );

        task.setOnFailed(e -> {
            setStatus("Erro ao baixar: " + task.getException().getMessage());
            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void showFiles() {
        VBox container = new VBox(12);
        container.getStyleClass().add("card");

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button backButton = new Button("Voltar");
        backButton.setGraphic(createIcon(FontAwesomeSolid.ARROW_LEFT, "muted-icon"));

        Button refreshButton = new Button("Atualizar");
        refreshButton.setGraphic(createIcon(FontAwesomeSolid.SYNC_ALT, "muted-icon"));

        TextField pathField = new TextField(currentPath);
        pathField.setEditable(false);
        pathField.setPrefWidth(600);

        ProgressBar uploadProgress = new ProgressBar(0);
        uploadProgress.setPrefWidth(220);
        uploadProgress.setVisible(false);

        toolbar.getChildren().addAll(backButton, refreshButton, pathField, uploadProgress);

        TableView<RemoteFileItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        table.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.A) {
                table.getSelectionModel().selectAll();
                event.consume();
            }

            if (event.getCode() == KeyCode.BACK_SPACE) {
                if (!currentPath.equals("/")) {
                    int lastSlash = currentPath.lastIndexOf("/");
                    currentPath = lastSlash <= 0 ? "/" : currentPath.substring(0, lastSlash);
                    pathField.setText(currentPath);
                    loadFiles(table, pathField);
                }
                event.consume();
            }

            if (event.isControlDown() && event.getCode() == KeyCode.R) {
                loadFiles(table, pathField);
                event.consume();
            }
        });

        setupUploadDragAndDrop(container, table, pathField, uploadProgress);
        setupUploadDragAndDrop(table, table, pathField, uploadProgress);

// Permite selecionar vários arquivos
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

// Ctrl + A = selecionar tudo
        table.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.A) {
                table.getSelectionModel().selectAll();
                event.consume();
            }
        });

        setupUploadDragAndDrop(container, table, pathField, uploadProgress);
        setupUploadDragAndDrop(table, table, pathField, uploadProgress);

        TableColumn<RemoteFileItem, String> nameColumn = new TableColumn<>("Nome");
        nameColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getName()));
        nameColumn.setPrefWidth(520);

        nameColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);

                if (empty || name == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                RemoteFileItem item = getTableView().getItems().get(getIndex());

                FontIcon icon = createFileIcon(item);
                Label fileName = new Label(name);
                fileName.getStyleClass().add("file-name");

                HBox row = new HBox(10, icon, fileName);
                row.setAlignment(Pos.CENTER_LEFT);

                setGraphic(row);
                setText(null);
            }
        });

        TableColumn<RemoteFileItem, String> typeColumn = new TableColumn<>("Tipo");
        typeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getType()));
        typeColumn.setPrefWidth(160);

        TableColumn<RemoteFileItem, String> sizeColumn = new TableColumn<>("Tamanho");
        sizeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getSize()));
        sizeColumn.setPrefWidth(120);

        table.getColumns().addAll(nameColumn, typeColumn, sizeColumn);

        container.getChildren().addAll(toolbar, table);
        contentArea.getChildren().setAll(container);

        refreshButton.setOnAction(e -> loadFiles(table, pathField));

        backButton.setOnAction(e -> {
            if (!currentPath.equals("/")) {
                int lastSlash = currentPath.lastIndexOf("/");
                currentPath = lastSlash <= 0 ? "/" : currentPath.substring(0, lastSlash);
                pathField.setText(currentPath);
                loadFiles(table, pathField);
            }
        });

        table.setRowFactory(tv -> {
            TableRow<RemoteFileItem> row = new TableRow<>();

            // Duplo clique
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {

                    RemoteFileItem item = row.getItem();

                    if (item.isDirectory()) {

                        currentPath = currentPath.equals("/")
                                ? "/" + item.getName()
                                : currentPath + "/" + item.getName();

                        pathField.setText(currentPath);
                        loadFiles(table, pathField);

                    } else if (isEditableFile(item)) {

                        openEditor(item);

                    } else {

                        setStatus("Esse tipo de arquivo ainda não pode ser editado.");

                    }
                }
            });

            // ===========================
            // MENU BOTÃO DIREITO
            // ===========================

            ContextMenu menu = new ContextMenu();

            MenuItem downloadItem = new MenuItem("📥 Baixar para...");
            downloadItem.setOnAction(e -> {
                RemoteFileItem item = row.getItem();

                if (item != null) {
                    downloadSelectedItems(table);
                }
            });

            MenuItem renameItem = new MenuItem("✏ Renomear");
            renameItem.setDisable(true); // implementar depois

            MenuItem deleteItem = new MenuItem("🗑 Excluir");
            deleteItem.setDisable(true); // implementar depois

            menu.getItems().addAll(
                    downloadItem,
                    new SeparatorMenuItem(),
                    renameItem,
                    deleteItem
            );

            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(menu)
            );

            return row;
        });

        setStatus("Tela: Arquivos");

        if (sftpService.isConnected()) {
            loadFiles(table, pathField);
        } else {
            setStatus("Conecte primeiro em Configurações.");
        }
    }

    private void setupUploadDragAndDrop(
            javafx.scene.Node dropTarget,
            TableView<RemoteFileItem> table,
            TextField pathField,
            ProgressBar uploadProgress
    ) {
        dropTarget.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles() && sftpService.isConnected()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });

        dropTarget.setOnDragDropped(event -> {
            var dragboard = event.getDragboard();

            if (!dragboard.hasFiles()) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            java.util.List<java.io.File> droppedFiles =
                    new java.util.ArrayList<>(dragboard.getFiles());

            Task<Void> uploadTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    int total = droppedFiles.size();

                    for (int i = 0; i < total; i++) {
                        java.io.File file = droppedFiles.get(i);

                        updateMessage("Enviando " + file.getName() + " (" + (i + 1) + "/" + total + ")");
                        updateProgress(i, total);

                        sftpService.uploadPath(file.toPath(), currentPath);

                        updateProgress(i + 1, total);
                    }

                    return null;
                }
            };

            uploadProgress.setVisible(true);
            uploadProgress.progressProperty().bind(uploadTask.progressProperty());
            statusLabel.textProperty().bind(uploadTask.messageProperty());

            uploadTask.setOnSucceeded(e -> {
                uploadProgress.progressProperty().unbind();
                statusLabel.textProperty().unbind();

                uploadProgress.setProgress(0);
                uploadProgress.setVisible(false);

                setStatus("Upload concluído.");
                loadFiles(table, pathField);

                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.5));

                pause.setOnFinished(ev -> setStatus("Tela: Arquivos"));
                pause.play();
            });

            uploadTask.setOnFailed(e -> {
                uploadProgress.progressProperty().unbind();
                statusLabel.textProperty().unbind();

                uploadProgress.setProgress(0);
                uploadProgress.setVisible(false);

                setStatus("Erro no upload: " + uploadTask.getException().getMessage());
                uploadTask.getException().printStackTrace();
            });

            Thread thread = new Thread(uploadTask);
            thread.setDaemon(true);
            thread.start();

            event.setDropCompleted(true);
            event.consume();
        });
    }

    private void loadFiles(TableView<RemoteFileItem> table, TextField pathField) {
        setStatus("Carregando arquivos de " + currentPath + "...");

        Task<java.util.List<RemoteFileItem>> task = new Task<>() {
            @Override
            protected java.util.List<RemoteFileItem> call() throws Exception {
                return sftpService.listFiles(currentPath);
            }
        };

        task.setOnSucceeded(e -> {
            table.setItems(FXCollections.observableArrayList(task.getValue()));
            pathField.setText(currentPath);
            setStatus("Arquivos carregados: " + task.getValue().size());
        });

        task.setOnFailed(e -> {
            setStatus("Erro ao listar arquivos: " + task.getException().getMessage());
            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private FontIcon createIcon(FontAwesomeSolid iconCode, String styleClass) {
        FontIcon icon = new FontIcon(iconCode);
        icon.getStyleClass().add(styleClass);
        icon.setIconSize(14);
        return icon;
    }

    private FontIcon createFileIcon(RemoteFileItem item) {
        String name = item.getName().toLowerCase();

        FontIcon icon;

        if (item.isDirectory()) {
            icon = new FontIcon(FontAwesomeSolid.FOLDER);
            icon.getStyleClass().add("folder-icon");
        } else if (name.endsWith(".jar")) {
            icon = new FontIcon(FontAwesomeSolid.PLUG);
            icon.getStyleClass().add("jar-icon");
        } else if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            icon = new FontIcon(FontAwesomeSolid.COG);
            icon.getStyleClass().add("config-icon");
        } else if (name.endsWith(".json")) {
            icon = new FontIcon(FontAwesomeSolid.CODE);
            icon.getStyleClass().add("json-icon");
        } else if (name.endsWith(".properties")) {
            icon = new FontIcon(FontAwesomeSolid.SLIDERS_H);
            icon.getStyleClass().add("properties-icon");
        } else if (name.endsWith(".log") || name.endsWith(".txt")) {
            icon = new FontIcon(FontAwesomeSolid.FILE_ALT);
            icon.getStyleClass().add("text-icon");
        } else if (name.endsWith(".zip") || name.endsWith(".rar")) {
            icon = new FontIcon(FontAwesomeSolid.FILE_ARCHIVE);
            icon.getStyleClass().add("archive-icon");
        } else {
            icon = new FontIcon(FontAwesomeSolid.FILE);
            icon.getStyleClass().add("file-icon");
        }

        icon.setIconSize(15);
        return icon;
    }

    private boolean isEditableFile(RemoteFileItem item) {
        String name = item.getName().toLowerCase();

        return name.endsWith(".yml")
                || name.endsWith(".yaml")
                || name.endsWith(".json")
                || name.endsWith(".properties")
                || name.endsWith(".txt")
                || name.endsWith(".log");
    }

    private String resolvePath(String fileName) {
        return currentPath.equals("/")
                ? "/" + fileName
                : currentPath + "/" + fileName;
    }

    private void openEditor(RemoteFileItem item) {
        String remotePath = resolvePath(item.getName());

        VBox box = new VBox(12);
        box.getStyleClass().add("card");
        box.setMaxHeight(Double.MAX_VALUE);

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button backButton = new Button("Voltar");
        backButton.setGraphic(createIcon(FontAwesomeSolid.ARROW_LEFT, "muted-icon"));

        Button saveButton = new Button("Salvar");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setGraphic(createIcon(FontAwesomeSolid.SAVE, "muted-icon"));

        Label title = new Label("Editando: " + remotePath);
        title.getStyleClass().add("page-title");

        toolbar.getChildren().addAll(backButton, saveButton, title);

        YamlEditor editor = new YamlEditor();
        VBox.setVgrow(editor, Priority.ALWAYS);

        box.getChildren().addAll(toolbar, editor);
        contentArea.getChildren().setAll(box);

        setStatus("Abrindo arquivo " + remotePath + "...");

        Task<String> loadTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return sftpService.readFile(remotePath);
            }
        };

        loadTask.setOnSucceeded(e -> {
            editor.setText(loadTask.getValue());
            editor.requestEditorFocus();
            setStatus("Arquivo aberto: " + item.getName());
        });

        loadTask.setOnFailed(e -> {
            setStatus("Erro ao abrir: " + loadTask.getException().getMessage());
            loadTask.getException().printStackTrace();
        });

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();

        saveButton.setOnAction(e -> {
            setStatus("Salvando " + item.getName() + "...");

            Task<Void> saveTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    sftpService.writeFile(remotePath, editor.getText());
                    return null;
                }
            };

            saveTask.setOnSucceeded(ev ->
                    setStatus("Arquivo salvo com sucesso: " + item.getName())
            );

            saveTask.setOnFailed(ev -> {
                setStatus("Erro ao salvar: " + saveTask.getException().getMessage());
                saveTask.getException().printStackTrace();
            });

            Thread saveThread = new Thread(saveTask);
            saveThread.setDaemon(true);
            saveThread.start();
        });

        backButton.setOnAction(e -> showFiles());
    }

    @FXML
    private void showTerminal() {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");

        Label title = new Label("Terminal do Servidor");
        title.getStyleClass().add("page-title");

        HBox powerButtons = new HBox(10);

        Button startButton = new Button("▶ Iniciar");
        startButton.getStyleClass().add("success-button");

        Button restartButton = new Button("↻ Reiniciar");

        Button stopButton = new Button("■ Parar");
        stopButton.getStyleClass().add("danger-button");

        Button killButton = new Button("✖ Kill");
        killButton.getStyleClass().add("danger-button");

        Button reconnectButton = new Button("Reconectar Console");

        powerButtons.getChildren().addAll(
                startButton,
                restartButton,
                stopButton,
                killButton,
                reconnectButton
        );

        TextArea console = new TextArea();
        console.setEditable(false);
        console.setPrefHeight(420);
        console.setText("Conectando ao console...\n");

        TextField commandField = new TextField();
        commandField.setPromptText("Digite um comando. Ex: say Olá mundo");

        Button sendButton = new Button("Enviar");
        sendButton.getStyleClass().add("primary-button");

        HBox commandBox = new HBox(10, commandField, sendButton);
        commandField.setPrefWidth(720);

        startButton.setOnAction(e -> runPowerAction("start", console));
        stopButton.setOnAction(e -> runPowerAction("stop", console));
        restartButton.setOnAction(e -> runPowerAction("restart", console));
        killButton.setOnAction(e -> runPowerAction("kill", console));
        reconnectButton.setOnAction(e -> connectConsoleWebSocket(console));

        sendButton.setOnAction(e -> {
            String command = commandField.getText().trim();

            if (command.isEmpty()) {
                return;
            }

            sendConsoleCommand(command, console);
            commandField.clear();
        });

        box.getChildren().addAll(
                title,
                powerButtons,
                console,
                commandBox
        );

        contentArea.getChildren().setAll(box);
        setStatus("Tela: Terminal");

        connectConsoleWebSocket(console);
    }

    @FXML
    private void showSettings() {
        VBox box = new VBox(15);
        box.setMaxWidth(460);
        box.getStyleClass().add("card");

        Label title = new Label("Conexão com o Servidor");
        title.setStyle("-fx-font-size:20px; -fx-font-weight:bold;");

        TextField hostField = new TextField();
        hostField.setPromptText("Host ou IP SFTP");

        TextField portField = new TextField("22");
        portField.setPromptText("Porta SSH/SFTP");

        TextField userField = new TextField();
        userField.setPromptText("Usuário SFTP");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Senha SFTP");

        Separator separator = new Separator();

        TextField panelUrlField = new TextField("https://panel.urubu.host");
        panelUrlField.setPromptText("URL do painel Pterodactyl");

        PasswordField apiKeyField = new PasswordField();
        apiKeyField.setPromptText("API Key do Pterodactyl");

        Button connectButton = new Button("Conectar");
        connectButton.setMaxWidth(Double.MAX_VALUE);

        connectButton.setOnAction(e -> {
            String host = hostField.getText().trim();
            String portText = portField.getText().trim();
            String user = userField.getText().trim();
            String password = passwordField.getText();

            String panelUrl = panelUrlField.getText().trim();
            String apiKey = apiKeyField.getText().trim();

            if (host.isEmpty() || portText.isEmpty() || user.isEmpty() || password.isEmpty()) {
                setStatus("Preencha host, porta, usuário e senha SFTP.");
                return;
            }

            if (panelUrl.isEmpty() || apiKey.isEmpty()) {
                setStatus("Preencha a URL e a API Key do Pterodactyl.");
                return;
            }
            int port;

            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException ex) {
                setStatus("Porta inválida.");
                return;
            }

            pteroService.configure(panelUrl, apiKey, "");

            setStatus("Conectando em " + host + ":" + port + "...");
            connectButton.setDisable(true);

            try {
                System.out.println("TESTANDO API DO PTERO...");
                System.out.println(pteroService.testConnection());
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    sftpService.connect(host, port, user, password);
                    return sftpService.isConnected();
                }
            };

            task.setOnSucceeded(event -> {
                connectButton.setDisable(false);

                if (!task.getValue()) {
                    setStatus("Falha ao conectar SFTP.");
                    return;
                }

                try {
                    var servers = pteroService.listServers();

                    serverSelector.getItems().setAll(servers);

                    if (servers.isEmpty()) {
                        setStatus("Conectado, mas nenhum servidor foi encontrado.");
                        return;
                    }

                    serverSelector.getSelectionModel().selectFirst();

                    PteroServer selected = serverSelector.getValue();

                    pteroService.selectServer(selected.getIdentifier());
                    serverSelector.setDisable(false);

                    currentPath = "/";

                    setStatus("Conectado. Servidor: " + selected.getName());

                    showFiles();

                } catch (Exception ex) {
                    setStatus("Erro ao carregar servidores.");
                    ex.printStackTrace();
                }
            });

            task.setOnFailed(event -> {
                connectButton.setDisable(false);
                setStatus("Erro: " + task.getException().getMessage());
                task.getException().printStackTrace();
            });

            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
        });

        box.getChildren().addAll(
                title,
                hostField,
                portField,
                userField,
                passwordField,
                separator,
                panelUrlField,
                apiKeyField,
                connectButton
        );

        contentArea.getChildren().setAll(box);
        setStatus("Tela: Configurações");
    }

    private void runPowerAction(String action, TextArea output) {
        if (!pteroService.isConfigured()) {
            setStatus("Configure a API do Pterodactyl primeiro.");
            return;
        }

        setStatus("Enviando ação: " + action);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return pteroService.power(action);
            }
        };

        task.setOnSucceeded(e -> {
            output.appendText("Ação enviada: " + action + "\n");
            setStatus("Comando enviado: " + action);
        });

        task.setOnFailed(e -> {
            output.appendText("Erro: " + task.getException().getMessage() + "\n");
            setStatus("Erro no Pterodactyl.");
            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void sendConsoleCommand(String command, TextArea console) {
        if (!pteroService.isConfigured()) {
            setStatus("Configure a API do Pterodactyl primeiro.");
            return;
        }

        console.appendText("> " + command + "\n");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return pteroService.sendCommand(command);
            }
        };

        task.setOnSucceeded(e -> {
            console.appendText("Comando enviado com sucesso.\n");
            setStatus("Comando enviado.");
        });

        task.setOnFailed(e -> {
            console.appendText("Erro: " + task.getException().getMessage() + "\n");
            setStatus("Erro ao enviar comando.");
            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void loadServerResources(TextArea output) {
        if (!pteroService.isConfigured()) {
            setStatus("Configure a API do Pterodactyl primeiro.");
            return;
        }

        setStatus("Buscando recursos do servidor...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return pteroService.getResources();
            }
        };

        task.setOnSucceeded(e -> {
            output.appendText("Resources:\n" + task.getValue() + "\n\n");
            setStatus("Recursos carregados.");
        });

        task.setOnFailed(e -> {
            output.appendText("Erro: " + task.getException().getMessage() + "\n");
            setStatus("Erro ao buscar recursos.");
            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void connectConsoleWebSocket(TextArea console) {
        if (!pteroService.isConfigured()) {
            console.appendText("Configure a API do Pterodactyl primeiro.\n");
            setStatus("Configure a API do Pterodactyl primeiro.");
            return;
        }

        Task<PterodactylService.WebSocketData> task = new Task<>() {
            @Override
            protected PterodactylService.WebSocketData call() throws Exception {
                return pteroService.getWebSocketData();
            }
        };

        task.setOnSucceeded(e -> {
            try {
                if (consoleSocket != null && consoleSocket.isOpen()) {
                    consoleSocket.close();
                }

                PterodactylService.WebSocketData data = task.getValue();

                consoleSocket = new PteroConsoleWebSocket(
                        data.socket(),
                        data.token(),
                        pteroService.getPanelUrl(),
                        console
                );

                consoleSocket.connect();

                setStatus("Console conectado.");

            } catch (Exception ex) {
                console.appendText("Erro ao conectar WebSocket: " + ex.getMessage() + "\n");
                setStatus("Erro ao conectar console.");
                ex.printStackTrace();
            }
        });

        task.setOnFailed(e -> {
            console.appendText("Erro ao obter WebSocket: " + task.getException().getMessage() + "\n");
            setStatus("Erro ao obter WebSocket.");
            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }



    private void setupWindowDrag() {
        titleBar.setOnMousePressed((MouseEvent event) -> {
            Stage stage = (Stage) titleBar.getScene().getWindow();
            xOffset = stage.getX() - event.getScreenX();
            yOffset = stage.getY() - event.getScreenY();
        });

        titleBar.setOnMouseDragged((MouseEvent event) -> {
            Stage stage = (Stage) titleBar.getScene().getWindow();

            if (!stage.isMaximized()) {
                stage.setX(event.getScreenX() + xOffset);
                stage.setY(event.getScreenY() + yOffset);
            }
        });

        titleBar.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                maximizeWindow();
            }
        });
    }

    @FXML
    private void minimizeWindow() {
        Stage stage = (Stage) contentArea.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void maximizeWindow() {
        Stage stage = (Stage) contentArea.getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
    }

    @FXML
    private void closeWindow() {
        Stage stage = (Stage) contentArea.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void refreshServerList() {
        if (pteroService == null || pteroService.getPanelUrl() == null || pteroService.getPanelUrl().isBlank()) {
            setStatus("Configure a API do Pterodactyl primeiro.");
            return;
        }

        setStatus("Atualizando lista de servidores...");

        Task<java.util.List<PteroServer>> task = new Task<>() {
            @Override
            protected java.util.List<PteroServer> call() throws Exception {
                return pteroService.listServers();
            }
        };

        task.setOnSucceeded(e -> {
            serverSelector.getItems().setAll(task.getValue());

            if (!task.getValue().isEmpty()) {
                serverSelector.getSelectionModel().selectFirst();
                PteroServer selected = serverSelector.getValue();
                pteroService.selectServer(selected.getIdentifier());
                serverSelector.setDisable(false);
                currentPath = "/";
                setStatus("Servidor selecionado: " + selected.getName());
                showFiles();
            } else {
                setStatus("Nenhum servidor encontrado.");
            }
        });

        task.setOnFailed(e -> {
            setStatus("Erro ao atualizar servidores.");
            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void setStatus(String text) {
        if (statusLabel.textProperty().isBound()) {
            statusLabel.textProperty().unbind();
        }

        statusLabel.setText(text);
    }

    private void downloadSelectedItems(TableView<RemoteFileItem> table) {

        List<RemoteFileItem> selected =
                new ArrayList<>(table.getSelectionModel().getSelectedItems());

        if (selected.isEmpty()) {
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Escolha onde salvar");

        File destination = chooser.showDialog(contentArea.getScene().getWindow());

        if (destination == null) {
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {

                int total = selected.size();

                for (int i = 0; i < total; i++) {

                    RemoteFileItem item = selected.get(i);

                    updateMessage("Baixando " + item.getName());

                    String remotePath = resolvePath(item.getName());

                    sftpService.downloadPath(remotePath, destination.toPath());

                    updateProgress(i + 1, total);
                }

                return null;
            }
        };

        uploadProgress.setVisible(true);
        uploadProgress.progressProperty().bind(task.progressProperty());

        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {

            uploadProgress.progressProperty().unbind();
            statusLabel.textProperty().unbind();

            uploadProgress.setVisible(false);

            setStatus("Download concluído.");

        });

        task.setOnFailed(e -> {

            uploadProgress.progressProperty().unbind();
            statusLabel.textProperty().unbind();

            uploadProgress.setVisible(false);

            setStatus("Erro ao baixar.");

            task.getException().printStackTrace();

        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

}