package com.relian.launcher.marketplace;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import java.io.File;
import java.nio.file.Path;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Live marketplace search UI for Modrinth and API-key-enabled CurseForge. */
public final class MarketplaceBrowserPane extends VBox {
    private static final String PANEL = "-fx-background-color:#20232a;-fx-background-radius:7;-fx-border-color:#30343d;-fx-border-radius:7;";
    private static final String FG = "#f2f4f7";
    private static final String MUTED = "#a6acb7";

    private final ProjectType fixedType;
    private final TextField search = new TextField();
    private final TextField minecraftVersion = new TextField("1.21.11");
    private final ComboBox<String> providerChoice = new ComboBox<>();
    private final ComboBox<String> loaderChoice = new ComboBox<>();
    private final VBox results = new VBox(10);
    private final Label status = new Label("Search Modrinth projects. CurseForge requires an API key configured by the launcher.");
    private final Button searchButton = new Button("Search");

    public MarketplaceBrowserPane(ProjectType fixedType) {
        this.fixedType = fixedType;
        setSpacing(14);
        setPadding(new Insets(4, 0, 0, 0));

        Label intro = label("Discover " + displayName(fixedType) + " from supported community marketplaces.");
        search.setPromptText("Search projects...");
        styleField(search);
        styleField(minecraftVersion);

        providerChoice.getItems().addAll("Modrinth", "CurseForge");
        providerChoice.setValue("Modrinth");
        styleCombo(providerChoice);

        loaderChoice.getItems().addAll("All loaders", "Fabric", "Forge", "NeoForge", "Quilt");
        loaderChoice.setValue("Fabric");
        styleCombo(loaderChoice);

        searchButton.setStyle("-fx-background-color:#4d83d8;-fx-background-radius:5;-fx-text-fill:white;-fx-font-weight:bold;-fx-padding:9 18;-fx-cursor:hand;");
        searchButton.setOnAction(event -> search());
        search.setOnAction(event -> search());

        HBox firstRow = new HBox(9, search);
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox secondRow = new HBox(9, providerChoice, minecraftVersion, loaderChoice, searchButton);
        secondRow.setAlignment(Pos.CENTER_LEFT);
        minecraftVersion.setPrefWidth(120);

        status.setWrapText(true);
        status.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:12px;");
        results.getChildren().add(label("Search to see available projects."));
        getChildren().addAll(intro, firstRow, secondRow, status, results);
    }

    private void search() {
        String query = search.getText() == null ? "" : search.getText().trim();
        String version = minecraftVersion.getText() == null ? "" : minecraftVersion.getText().trim();
        String loader = loaderChoice.getValue();
        if ("All loaders".equals(loader)) loader = "";
        final String selectedLoader = loader;
        final String selectedProvider = providerChoice.getValue();

        MarketplaceProvider provider;
        if ("CurseForge".equals(selectedProvider)) {
            provider = new CurseForgeProvider(System.getenv("RELIAN_CURSEFORGE_API_KEY"));
            if (!((CurseForgeProvider) provider).isConfigured()) {
                setStatus("CurseForge is selected, but no API key is configured. Set RELIAN_CURSEFORGE_API_KEY in the launcher's environment; no key is embedded in Relian.");
                return;
            }
        } else {
            provider = new ModrinthProvider();
        }

        searchButton.setDisable(true);
        status.setText("Searching " + selectedProvider + "...");
        results.getChildren().clear();
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(28, 28);
        results.getChildren().add(progress);

        MarketplaceProvider selected = provider;
        CompletableFuture.supplyAsync(() -> {
            try {
                return selected.search(query, fixedType, version, selectedLoader, 30);
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        }).whenComplete((projects, error) -> Platform.runLater(() -> {
            searchButton.setDisable(false);
            results.getChildren().clear();
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                setStatus("Search failed: " + safeMessage(cause));
                return;
            }
            if (projects.isEmpty()) {
                setStatus("No matching projects were returned. Try a different search or version.");
                return;
            }
            setStatus("Found " + projects.size() + " projects on " + selectedProvider + ".");
            for (MarketplaceProject project : projects) results.getChildren().add(projectCard(project));
        }));
    }

    private VBox projectCard(MarketplaceProject project) {
        VBox card = new VBox(7);
        card.setPadding(new Insets(14));
        card.setStyle(PANEL);

        Label title = label(project.title());
        title.setStyle("-fx-text-fill:" + FG + ";-fx-font-size:14px;-fx-font-weight:bold;");
        Label metadata = label(project.source() + "  •  by " + project.author() + "  •  " + project.type());
        metadata.setStyle("-fx-text-fill:#8fa0b8;-fx-font-size:10px;");
        Label description = label(project.description());
        description.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:12px;");

        Button open = new Button("View project");
        open.setStyle("-fx-background-color:#303b4d;-fx-background-radius:4;-fx-text-fill:#e8edf5;-fx-padding:7 12;-fx-cursor:hand;");
        open.setOnAction(event -> openProject(project.projectUrl()));
        HBox actions = new HBox(8, open);
        if (project.type() != ProjectType.MODPACK) {
            Button install = new Button("Install");
            install.setStyle("-fx-background-color:#315b45;-fx-background-radius:4;-fx-text-fill:#e8fff0;-fx-padding:7 12;-fx-cursor:hand;");
            install.setOnAction(event -> chooseInstallDirectory(project, install));
            actions.getChildren().add(install);
        }
        card.getChildren().addAll(title, metadata, description, actions);
        return card;
    }


    private void chooseInstallDirectory(MarketplaceProject project, Button installButton) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose your Minecraft game directory");
        File selectedDirectory = chooser.showDialog(getScene().getWindow());
        if (selectedDirectory == null) return;
        String version = minecraftVersion.getText() == null ? "" : minecraftVersion.getText().trim();
        String loader = loaderChoice.getValue();
        if ("All loaders".equals(loader)) loader = "";
        final String selectedLoader = loader;
        installButton.setDisable(true);
        setStatus("Checking compatibility and installing " + project.title() + "...");
        CompletableFuture.supplyAsync(() -> {
            try {
                if ("Modrinth".equalsIgnoreCase(project.source())) {
                    return ModrinthInstaller.install(project, version, selectedLoader, selectedDirectory.toPath());
                }
                String key = System.getenv("RELIAN_CURSEFORGE_API_KEY");
                return CurseForgeInstaller.install(project, version, selectedLoader, selectedDirectory.toPath(), key);
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        }).whenComplete((installedPath, error) -> Platform.runLater(() -> {
            installButton.setDisable(false);
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                setStatus("Install failed: " + safeMessage(cause));
                return;
            }
            setStatus("Installed and checksum-verified: " + installedPath.getFileName());
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("Relian Client");
            alert.setHeaderText("Installation complete");
            alert.setContentText("Installed " + project.title() + " to:\\n" + installedPath);
            alert.showAndWait();
        }));
    }

    private void openProject(URI uri) {
        try {
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("Only secure HTTPS project links are allowed.");
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                setStatus("Your system does not expose a browser-opening action. Project link: " + uri);
            }
        } catch (Exception ex) {
            setStatus("Could not open project page: " + safeMessage(ex));
        }
    }

    private void setStatus(String message) {
        status.setText(message);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static String displayName(ProjectType type) {
        return switch (type) {
            case MOD -> "mods";
            case MODPACK -> "modpacks";
            case RESOURCE_PACK -> "resource packs";
            case SHADER -> "shaders";
        };
    }

    private static Label label(String text) {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill:" + MUTED + ";-fx-font-size:12px;");
        return label;
    }

    private static void styleField(TextField field) {
        field.setStyle("-fx-control-inner-background:#292e37;-fx-text-fill:" + FG + ";-fx-prompt-text-fill:#7f8794;");
    }

    private static void styleCombo(ComboBox<String> combo) {
        combo.setStyle("-fx-background-color:#292e37;-fx-mark-color:#d7dce5;-fx-font-size:12px;");
    }
}
