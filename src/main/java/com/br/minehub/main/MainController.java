package com.br.minehub.main;

import com.br.minehub.main.editor.YamlEditor;
import com.br.minehub.main.model.RemoteFileItem;
import com.br.minehub.main.service.SftpService;
import com.br.minehub.main.service.pterodactyl.PterodactylService;
import com.br.minehub.main.service.pterodactyl.model.PteroServer;
import com.br.minehub.main.service.pterodactyl.ws.PteroConsoleWebSocket;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class MainController {

    private final PterodactylService pteroService = new PterodactylService();
    private final SftpService sftpService = new SftpService();
    private PteroConsoleWebSocket consoleSocket;
    private String currentPath = "/";

    @FXML
    private StackPane contentArea;

    @FXML
    private ComboBox<PteroServer> serverSelector;

    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        serverSelector.setDisable(true);

        serverSelector.setOnAction(e -> {
            PteroServer server = serverSelector.getValue();

            if (server == null) {
                return;
            }

            pteroService.selectServer(server.getIdentifier());
            currentPath = "/";

            statusLabel.setText("Servidor selecionado: " + server.getName());

            showFiles();
        });

        showFiles();
        statusLabel.setText("MineHub iniciado");
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

        toolbar.getChildren().addAll(backButton, refreshButton, pathField);

        TableView<RemoteFileItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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
                        statusLabel.setText("Esse tipo de arquivo ainda não pode ser editado.");
                    }
                }
            });

            return row;
        });

        statusLabel.setText("Tela: Arquivos");

        if (sftpService.isConnected()) {
            loadFiles(table, pathField);
        } else {
            statusLabel.setText("Conecte primeiro em Configurações.");
        }
    }

    private void loadFiles(TableView<RemoteFileItem> table, TextField pathField) {
        statusLabel.setText("Carregando arquivos de " + currentPath + "...");

        Task<java.util.List<RemoteFileItem>> task = new Task<>() {
            @Override
            protected java.util.List<RemoteFileItem> call() throws Exception {
                return sftpService.listFiles(currentPath);
            }
        };

        task.setOnSucceeded(e -> {
            table.setItems(FXCollections.observableArrayList(task.getValue()));
            pathField.setText(currentPath);
            statusLabel.setText("Arquivos carregados: " + task.getValue().size());
        });

        task.setOnFailed(e -> {
            statusLabel.setText("Erro ao listar arquivos: " + task.getException().getMessage());
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

        statusLabel.setText("Abrindo arquivo " + remotePath + "...");

        Task<String> loadTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                return sftpService.readFile(remotePath);
            }
        };

        loadTask.setOnSucceeded(e -> {
            editor.setText(loadTask.getValue());
            editor.requestEditorFocus();
            statusLabel.setText("Arquivo aberto: " + item.getName());
        });

        loadTask.setOnFailed(e -> {
            statusLabel.setText("Erro ao abrir: " + loadTask.getException().getMessage());
            loadTask.getException().printStackTrace();
        });

        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();

        saveButton.setOnAction(e -> {
            statusLabel.setText("Salvando " + item.getName() + "...");

            Task<Void> saveTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    sftpService.writeFile(remotePath, editor.getText());
                    return null;
                }
            };

            saveTask.setOnSucceeded(ev ->
                    statusLabel.setText("Arquivo salvo com sucesso: " + item.getName())
            );

            saveTask.setOnFailed(ev -> {
                statusLabel.setText("Erro ao salvar: " + saveTask.getException().getMessage());
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
        statusLabel.setText("Tela: Terminal");

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
                statusLabel.setText("Preencha host, porta, usuário e senha SFTP.");
                return;
            }

            if (panelUrl.isEmpty() || apiKey.isEmpty()) {
                statusLabel.setText("Preencha a URL e a API Key do Pterodactyl.");
                return;
            }
            int port;

            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException ex) {
                statusLabel.setText("Porta inválida.");
                return;
            }

            pteroService.configure(panelUrl, apiKey, "");

            statusLabel.setText("Conectando em " + host + ":" + port + "...");
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
                    statusLabel.setText("Falha ao conectar SFTP.");
                    return;
                }

                try {
                    var servers = pteroService.listServers();

                    serverSelector.getItems().setAll(servers);

                    if (servers.isEmpty()) {
                        statusLabel.setText("Conectado, mas nenhum servidor foi encontrado.");
                        return;
                    }

                    serverSelector.getSelectionModel().selectFirst();

                    PteroServer selected = serverSelector.getValue();

                    pteroService.selectServer(selected.getIdentifier());
                    serverSelector.setDisable(false);

                    currentPath = "/";

                    statusLabel.setText("Conectado. Servidor: " + selected.getName());

                    showFiles();

                } catch (Exception ex) {
                    statusLabel.setText("Erro ao carregar servidores.");
                    ex.printStackTrace();
                }
            });

            task.setOnFailed(event -> {
                connectButton.setDisable(false);
                statusLabel.setText("Erro: " + task.getException().getMessage());
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
        statusLabel.setText("Tela: Configurações");
    }

    private void runPowerAction(String action, TextArea output) {
        if (!pteroService.isConfigured()) {
            statusLabel.setText("Configure a API do Pterodactyl primeiro.");
            return;
        }

        statusLabel.setText("Enviando ação: " + action);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return pteroService.power(action);
            }
        };

        task.setOnSucceeded(e -> {
            output.appendText("Ação enviada: " + action + "\n");
            statusLabel.setText("Comando enviado: " + action);
        });

        task.setOnFailed(e -> {
            output.appendText("Erro: " + task.getException().getMessage() + "\n");
            statusLabel.setText("Erro no Pterodactyl.");
            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void sendConsoleCommand(String command, TextArea console) {
        if (!pteroService.isConfigured()) {
            statusLabel.setText("Configure a API do Pterodactyl primeiro.");
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
            statusLabel.setText("Comando enviado.");
        });

        task.setOnFailed(e -> {
            console.appendText("Erro: " + task.getException().getMessage() + "\n");
            statusLabel.setText("Erro ao enviar comando.");
            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void loadServerResources(TextArea output) {
        if (!pteroService.isConfigured()) {
            statusLabel.setText("Configure a API do Pterodactyl primeiro.");
            return;
        }

        statusLabel.setText("Buscando recursos do servidor...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return pteroService.getResources();
            }
        };

        task.setOnSucceeded(e -> {
            output.appendText("Resources:\n" + task.getValue() + "\n\n");
            statusLabel.setText("Recursos carregados.");
        });

        task.setOnFailed(e -> {
            output.appendText("Erro: " + task.getException().getMessage() + "\n");
            statusLabel.setText("Erro ao buscar recursos.");
            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void connectConsoleWebSocket(TextArea console) {
        if (!pteroService.isConfigured()) {
            console.appendText("Configure a API do Pterodactyl primeiro.\n");
            statusLabel.setText("Configure a API do Pterodactyl primeiro.");
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

                statusLabel.setText("Console conectado.");

            } catch (Exception ex) {
                console.appendText("Erro ao conectar WebSocket: " + ex.getMessage() + "\n");
                statusLabel.setText("Erro ao conectar console.");
                ex.printStackTrace();
            }
        });

        task.setOnFailed(e -> {
            console.appendText("Erro ao obter WebSocket: " + task.getException().getMessage() + "\n");
            statusLabel.setText("Erro ao obter WebSocket.");
            task.getException().printStackTrace();
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

}