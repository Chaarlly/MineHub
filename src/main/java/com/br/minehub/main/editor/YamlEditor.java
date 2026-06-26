package com.br.minehub.main.editor;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YamlEditor extends VBox {

    private final CodeArea codeArea = new CodeArea();
    private final Label errorLabel = new Label();
    private final Yaml yaml = new Yaml();

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
        getStyleClass().add("yaml-editor");
        setSpacing(8);

        errorLabel.getStyleClass().add("yaml-status");
        errorLabel.setText("YAML pronto para edição");

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
        getChildren().addAll(errorLabel, codeArea);
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

        if (diagnostic == null) {
            errorLabel.setText("✔ YAML válido");
            errorLabel.getStyleClass().setAll("yaml-status", "yaml-ok");
            codeArea.getStyleClass().remove("editor-error");
            if (!codeArea.getStyleClass().contains("editor-ok")) {
                codeArea.getStyleClass().add("editor-ok");
            }
            return;
        }

        errorLabel.setText("✖ Linha " + diagnostic.line() + ", coluna " + diagnostic.column() + " • " + diagnostic.message());
        errorLabel.getStyleClass().setAll("yaml-status", "yaml-error");

        codeArea.getStyleClass().remove("editor-ok");
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
                        "YAML não aceita TAB. Use espaços."
                );
            }
        }

        try {
            if (!text.isBlank()) {
                yaml.load(text);
            }

            return null;

        } catch (MarkedYAMLException ex) {
            if (ex.getProblemMark() != null) {
                return new Diagnostic(
                        ex.getProblemMark().getLine() + 1,
                        ex.getProblemMark().getColumn() + 1,
                        cleanMessage(ex.getProblem())
                );
            }

            return new Diagnostic(1, 1, cleanMessage(ex.getMessage()));

        } catch (Exception ex) {
            return new Diagnostic(1, 1, cleanMessage(ex.getMessage()));
        }
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
                .trim();
    }

    private void markErrorLine(int lineNumber) {
        int paragraphIndex = Math.max(0, lineNumber - 1);
        int totalLines = codeArea.getText().split("\n", -1).length;

        if (paragraphIndex < totalLines) {
            codeArea.setParagraphStyle(paragraphIndex, Collections.singleton("line-error"));
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

    private record Diagnostic(int line, int column, String message) {
    }
}