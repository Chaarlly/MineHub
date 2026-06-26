package com.br.minehub.main.editor;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YamlEditor extends VBox {

    private final CodeArea codeArea = new CodeArea();
    private final Label statusLabel = new Label();
    private final Button fixButton = new Button("Aplicar correção segura");
    private final Yaml yaml;

    private Diagnostic lastDiagnostic;

    private static final Pattern YAML_PATTERN = Pattern.compile(
            "(?<COMMENT>#.*$)"
                    + "|(?<STRING>\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')"
                    + "|(?<BOOLEAN>\\b(true|false|null)\\b)"
                    + "|(?<NUMBER>\\b\\d+(\\.\\d+)?\\b)"
                    + "|(?<LIST>^\\s*-)"
                    + "|(?<KEY>^\\s*[a-zA-Z0-9_.-]+\\s*:)",
            Pattern.MULTILINE
    );

    public YamlEditor() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        this.yaml = new Yaml(options);

        getStyleClass().add("yaml-editor");
        setSpacing(8);

        statusLabel.getStyleClass().add("yaml-status");
        statusLabel.setText("YAML pronto para edição");

        fixButton.getStyleClass().add("primary-button");
        fixButton.setVisible(false);
        fixButton.setManaged(false);
        fixButton.setOnAction(e -> applyAutoFix());

        HBox statusBox = new HBox(10, statusLabel, fixButton);

        codeArea.getStyleClass().add("code-area");
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setWrapText(false);

        codeArea.textProperty().addListener((obs, oldText, newText) -> validateAndHighlight());

        codeArea.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.TAB) {
                event.consume();
                codeArea.replaceSelection("  ");
            }
        });

        VBox.setVgrow(codeArea, Priority.ALWAYS);
        getChildren().addAll(statusBox, codeArea);
    }

    public Node getView() {
        return this;
    }

    public void setText(String text) {
        codeArea.replaceText(text == null ? "" : text);
        validateAndHighlight();
    }

    public String getText() {
        return codeArea.getText();
    }

    public void requestEditorFocus() {
        codeArea.requestFocus();
    }

    private void validateAndHighlight() {
        String text = codeArea.getText();

        codeArea.setStyleSpans(0, computeHighlighting(text));
        clearLineErrors();

        Diagnostic diagnostic = validateYaml(text);
        lastDiagnostic = diagnostic;

        if (diagnostic == null) {
            statusLabel.setText("✔ YAML válido");
            statusLabel.getStyleClass().setAll("yaml-status", "yaml-ok");

            fixButton.setVisible(false);
            fixButton.setManaged(false);

            codeArea.getStyleClass().remove("editor-error");
            return;
        }

        statusLabel.setText(
                "✖ Linha " + diagnostic.line()
                        + ", coluna " + diagnostic.column()
                        + " • " + diagnostic.problem()
                        + " | Como resolver: " + diagnostic.solution()
        );

        statusLabel.getStyleClass().setAll("yaml-status", "yaml-error");

        boolean canFix = diagnostic.fixType() != FixType.NONE;
        fixButton.setVisible(canFix);
        fixButton.setManaged(canFix);

        if (!codeArea.getStyleClass().contains("editor-error")) {
            codeArea.getStyleClass().add("editor-error");
        }

        markErrorLine(diagnostic.line());
    }

    private Diagnostic validateYaml(String text) {
        String[] lines = text.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            int tabIndex = line.indexOf('\t');

            if (tabIndex >= 0) {
                return new Diagnostic(
                        i + 1,
                        tabIndex + 1,
                        "TAB não é permitido no YAML.",
                        "substitua o TAB por 2 espaços.",
                        FixType.REPLACE_TAB
                );
            }

            if (hasInvisibleCharacter(line)) {
                return new Diagnostic(
                        i + 1,
                        1,
                        "Caractere invisível encontrado.",
                        "apague a linha e digite novamente.",
                        FixType.REMOVE_INVISIBLE
                );
            }
        }

        try {
            if (!text.isBlank()) {
                yaml.load(text);
            }

            return null;

        } catch (MarkedYAMLException ex) {
            int line = 1;
            int column = 1;

            if (ex.getProblemMark() != null) {
                line = ex.getProblemMark().getLine() + 1;
                column = ex.getProblemMark().getColumn() + 1;
            }

            String rawProblem = ex.getProblem();

            return new Diagnostic(
                    line,
                    column,
                    cleanMessage(rawProblem),
                    suggestFix(rawProblem),
                    detectFixType(rawProblem)
            );

        } catch (Exception ex) {
            return new Diagnostic(
                    1,
                    1,
                    cleanMessage(ex.getMessage()),
                    "verifique aspas, dois-pontos, chaves duplicadas e indentação.",
                    FixType.NONE
            );
        }
    }

    private void applyAutoFix() {
        if (lastDiagnostic == null || lastDiagnostic.fixType() == FixType.NONE) {
            return;
        }

        String original = codeArea.getText();
        String fixed = buildFixedText(original, lastDiagnostic);

        if (fixed == null || fixed.equals(original)) {
            showFixFailed();
            return;
        }

        if (tryApplyFixSafely(fixed)) {
            statusLabel.setText("✔ Correção aplicada com segurança.");
            statusLabel.getStyleClass().setAll("yaml-status", "yaml-ok");
        } else {
            showFixFailed();
        }
    }

    private boolean tryApplyFixSafely(String fixedText) {
        String original = codeArea.getText();

        codeArea.replaceText(fixedText);

        Diagnostic afterFix = validateYaml(codeArea.getText());

        if (afterFix == null) {
            validateAndHighlight();
            return true;
        }

        codeArea.replaceText(original);
        validateAndHighlight();
        return false;
    }

    private String buildFixedText(String original, Diagnostic diagnostic) {
        String[] lines = original.split("\n", -1);
        int index = diagnostic.line() - 1;

        if (index < 0 || index >= lines.length) {
            return null;
        }

        String line = lines[index];

        switch (diagnostic.fixType()) {
            case REPLACE_TAB -> lines[index] = line.replace("\t", "  ");

            case REMOVE_INVISIBLE -> lines[index] = removeInvisibleCharacters(line);

            case ADD_MISSING_COLON -> {
                if (!line.trim().endsWith(":")) {
                    lines[index] = line + ":";
                }
            }

            case QUOTE_VALUE_WITH_COLON -> {
                int colon = line.indexOf(":");

                if (colon < 0) {
                    return null;
                }

                String prefix = line.substring(0, colon + 1);
                String value = line.substring(colon + 1).trim();

                if (value.isBlank() || value.startsWith("\"") || value.startsWith("'")) {
                    return null;
                }

                lines[index] = prefix + " \"" + value.replace("\"", "\\\"") + "\"";
            }

            case NONE -> {
                return null;
            }
        }

        return String.join("\n", lines);
    }

    private void showFixFailed() {
        statusLabel.setText("✖ Não foi possível aplicar correção automática sem risco de quebrar o YAML.");
        statusLabel.getStyleClass().setAll("yaml-status", "yaml-error");
    }

    private FixType detectFixType(String problem) {
        if (problem == null) {
            return FixType.NONE;
        }

        String p = problem.toLowerCase();

        if (p.contains("tab")) {
            return FixType.REPLACE_TAB;
        }

        if (p.contains("could not find expected ':'") || p.contains("while scanning a simple key")) {
            return FixType.ADD_MISSING_COLON;
        }

        if (p.contains("mapping values are not allowed")) {
            return FixType.QUOTE_VALUE_WITH_COLON;
        }

        return FixType.NONE;
    }

    private boolean hasInvisibleCharacter(String line) {
        for (char c : line.toCharArray()) {
            if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
                return true;
            }
        }

        return false;
    }

    private String removeInvisibleCharacters(String line) {
        StringBuilder builder = new StringBuilder();

        for (char c : line.toCharArray()) {
            if (!Character.isISOControl(c) || c == '\n' || c == '\r' || c == '\t') {
                builder.append(c);
            }
        }

        return builder.toString();
    }

    private String cleanMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Erro de sintaxe YAML.";
        }

        return message
                .replace("found character '\\t(TAB)' that cannot start any token", "TAB não é permitido no YAML.")
                .replace("mapping values are not allowed here", "valor ou indentação inválida.")
                .replace("could not find expected ':'", "faltou ':' em alguma chave.")
                .replace("while scanning a simple key", "chave YAML inválida.")
                .replace("found unexpected end of stream", "fim inesperado do arquivo.")
                .replace("expected <block end>", "fim de bloco esperado.")
                .replace("found duplicate key", "chave duplicada encontrada.")
                .trim();
    }

    private String suggestFix(String problem) {
        if (problem == null || problem.isBlank()) {
            return "verifique a estrutura do YAML nessa região.";
        }

        String p = problem.toLowerCase();

        if (p.contains("tab")) {
            return "troque TAB por espaços.";
        }

        if (p.contains("mapping values are not allowed")) {
            return "provavelmente existe ':' dentro de um texto. Coloque o valor entre aspas.";
        }

        if (p.contains("could not find expected ':'")) {
            return "adicione ':' depois da chave ou corrija a linha anterior.";
        }

        if (p.contains("while scanning a simple key")) {
            return "verifique se a chave tem ':' no final.";
        }

        if (p.contains("expected <block end>")) {
            return "confira se a indentação voltou para o nível correto.";
        }

        if (p.contains("unexpected end")) {
            return "verifique se alguma aspas simples, aspas duplas ou bloco ficou aberto.";
        }

        if (p.contains("duplicate key")) {
            return "remova ou renomeie uma das chaves repetidas.";
        }

        return "verifique indentação, aspas e ':' perto da linha indicada.";
    }

    private void markErrorLine(int lineNumber) {
        int paragraphIndex = Math.max(0, lineNumber - 1);
        int totalLines = codeArea.getText().split("\n", -1).length;

        if (paragraphIndex < totalLines) {
            codeArea.setParagraphStyle(paragraphIndex, Collections.singleton("line-error"));
            codeArea.moveTo(paragraphIndex, 0);
            codeArea.requestFollowCaret();
        }
    }

    private void clearLineErrors() {
        int totalLines = codeArea.getText().split("\n", -1).length;

        for (int i = 0; i < totalLines; i++) {
            codeArea.setParagraphStyle(i, Collections.emptyList());
        }
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = YAML_PATTERN.matcher(text);
        int lastKwEnd = 0;

        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass =
                    matcher.group("COMMENT") != null ? "yaml-comment" :
                            matcher.group("STRING") != null ? "yaml-string" :
                            matcher.group("BOOLEAN") != null ? "yaml-boolean" :
                            matcher.group("NUMBER") != null ? "yaml-number" :
                            matcher.group("LIST") != null ? "yaml-list" :
                            matcher.group("KEY") != null ? "yaml-key" :
                            null;

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());

            lastKwEnd = matcher.end();
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private enum FixType {
        NONE,
        REPLACE_TAB,
        REMOVE_INVISIBLE,
        ADD_MISSING_COLON,
        QUOTE_VALUE_WITH_COLON
    }

    private record Diagnostic(
            int line,
            int column,
            String problem,
            String solution,
            FixType fixType
    ) {
    }
}