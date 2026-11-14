package com.relian.launcher;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;
import java.io.File;
import java.util.prefs.Preferences;
import javafx.stage.Modality;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class RelianLauncher extends Application {
    private static final String BG="#17191e", PANEL="#20232a", ALT="#292e37", FG="#f2f4f7", MUTED="#a6acb7", BLUE="#4d83d8";
    private final Preferences preferences = Preferences.userNodeForPackage(RelianLauncher.class);
    private String backgroundColor = preferences.get("background.color", BG);
    private int memoryLimitMb = Math.max(1024, Math.min(6144, preferences.getInt("memory.maxMb", 4096)));
    private String gameDirectory = preferences.get("game.directory", "");
    private java.nio.file.Path activeInstanceDirectory = null;
    private Runnable refreshInstancesAction = () -> {};
    private String activeInstanceVersion = preferences.get("active.instance.version", "");
    private String activeInstanceLoader = preferences.get("active.instance.loader", "");
    private String activeInstanceProfileId = preferences.get("active.instance.profileId", "");
    private final BorderPane shell = new BorderPane();
    private final StackPane host = new StackPane();
    private ComboBox<String> versions;
    private String active = "Home";
    private String accountLabel = "Sign in with Microsoft";
    private com.relian.launcher.runtime.MinecraftLaunchCommandBuilder.Session minecraftSession;
    private OfflineTestProfile offlineTestProfile;
    private final com.relian.launcher.runtime.MinecraftLaunchService launchService = new com.relian.launcher.runtime.MinecraftLaunchService();
    private final List<String> versionList = new ArrayList<>(List.of("26.3","26.2","1.21.11","1.21.10","1.20.6","1.20.1","1.19.4","1.18.2","1.8.9","1.8.8"));

    @Override public void stop() { launchService.close(); }

    @Override public void start(Stage stage) {
        String savedInstance = preferences.get("active.instance.path", "");
        if (!savedInstance.isBlank()) {
            try {
                java.nio.file.Path candidate = java.nio.file.Path.of(savedInstance).toAbsolutePath().normalize();
                if (java.nio.file.Files.isDirectory(candidate) && java.nio.file.Files.isRegularFile(candidate.resolve("relian-instance.json"))) {
                    activeInstanceDirectory = candidate;
                    activeInstanceProfileId = readLoaderProfileId(candidate);
                }
            } catch (RuntimeException ignored) { activeInstanceDirectory = null; }
        }
        shell.setStyle("-fx-background-color:"+backgroundColor+";");
        shell.setLeft(sidebar());
        shell.setCenter(host);
        page("Home");
        stage.setTitle("Relian Client");
        stage.setMinWidth(900); stage.setMinHeight(600);
        stage.setScene(new Scene(shell,1120,700));
        stage.show();
        loadVersionCatalog();
    }


    private void loadVersionCatalog() {
        CompletableFuture.supplyAsync(() -> {
            try {
                return new com.relian.launcher.catalog.MinecraftVersionCatalog().fetchRelianTargets();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }).whenComplete((releases, error) -> javafx.application.Platform.runLater(() -> {
            if (error != null || releases == null || releases.isEmpty()) return;
            String selected = versions == null ? null : versions.getValue();
            versionList.clear();
            for (com.relian.launcher.catalog.MinecraftRelease release : releases) versionList.add(release.id());
            if (versions != null) {
                versions.getItems().setAll(versionList);
                if (selected != null && versionList.contains(selected)) versions.setValue(selected);
                else if (!versionList.isEmpty()) versions.setValue(versionList.get(0));
            }
            if ("Versions".equals(active)) page("Versions");
        }));
    }

    private VBox sidebar() {
        VBox v=new VBox(8); v.setPadding(new Insets(24,14,20,14)); v.setPrefWidth(205);
        v.setStyle("-fx-background-color:#13151a;-fx-border-color:#2b2e36;-fx-border-width:0 1 0 0;");
        HBox brand=new HBox(10); brand.setAlignment(Pos.CENTER_LEFT);
        Label r=new Label("R"); r.setMinSize(38,38); r.setAlignment(Pos.CENTER);
        r.setStyle("-fx-background-color:#2d4264;-fx-background-radius:8;-fx-text-fill:white;-fx-font-size:22px;-fx-font-weight:bold;");
        VBox bt=new VBox(1); Label n=new Label("RELIAN"), sub=new Label("CLIENT");
        n.setStyle("-fx-text-fill:"+FG+";-fx-font-size:16px;-fx-font-weight:bold;");
        sub.setStyle("-fx-text-fill:"+MUTED+";-fx-font-size:9px;");
        bt.getChildren().addAll(n,sub); brand.getChildren().addAll(r,bt);
        v.getChildren().add(brand); Region gap=new Region(); gap.setPrefHeight(20); v.getChildren().add(gap);
        for(String s:List.of("Home","Instances","Versions","Mods","Modpacks","Resource Packs","Shaders","Installed Content","Settings")) {
            Button b=new Button(s); b.setMaxWidth(Double.MAX_VALUE); b.setAlignment(Pos.CENTER_LEFT);
            b.setPadding(new Insets(11,12,11,12)); b.setOnAction(e->page(s)); b.setId("nav-"+s);
            v.getChildren().add(b);
        }
        Region push=new Region(); VBox.setVgrow(push,Priority.ALWAYS);
        Label foot=new Label("RELIAN • EARLY ACCESS"); foot.setStyle("-fx-text-fill:#69717f;-fx-font-size:9px;");
        v.getChildren().addAll(push,foot); return v;
    }

    private void page(String name) {
        active=name; host.getChildren().clear();
        VBox content=new VBox(18); content.setPadding(new Insets(30,38,30,38));
        HBox header=new HBox(); header.setAlignment(Pos.CENTER_LEFT);
        Label title=new Label(name); title.setStyle("-fx-text-fill:"+FG+";-fx-font-size:25px;-fx-font-weight:bold;");
        Region spacer=new Region(); HBox.setHgrow(spacer,Priority.ALWAYS);
        Button account=small(accountLabel);
        account.setOnAction(e -> signIn());
        header.getChildren().addAll(title,spacer,account); content.getChildren().add(header);
        switch(name) {
            case "Home" -> content.getChildren().add(home());
            case "Instances" -> content.getChildren().add(instancesPage());
            case "Versions" -> content.getChildren().add(versionPage());
            case "Mods" -> content.getChildren().add(marketplace(com.relian.launcher.marketplace.ProjectType.MOD));
            case "Modpacks" -> content.getChildren().add(marketplace(com.relian.launcher.marketplace.ProjectType.MODPACK));
            case "Resource Packs" -> content.getChildren().add(marketplace(com.relian.launcher.marketplace.ProjectType.RESOURCE_PACK));
            case "Shaders" -> content.getChildren().add(marketplace(com.relian.launcher.marketplace.ProjectType.SHADER));
            case "Installed Content" -> content.getChildren().add(installedContentPage());
            default -> content.getChildren().add(settings());
        }
        ScrollPane scroll=new ScrollPane(content); scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:"+backgroundColor+";-fx-background-color:"+backgroundColor+";-fx-border-color:transparent;");
        host.getChildren().add(scroll);
        if(shell.getLeft() instanceof VBox side) for(var node:side.getChildren()) if(node instanceof Button b) b.setStyle(navStyle(b.getText().equals(name)));
    }

    private com.relian.launcher.marketplace.MarketplaceBrowserPane marketplace(com.relian.launcher.marketplace.ProjectType type) {
        java.nio.file.Path preferred = activeInstanceDirectory != null ? activeInstanceDirectory
            : (gameDirectory.isBlank() ? null : java.nio.file.Path.of(gameDirectory));
        return new com.relian.launcher.marketplace.MarketplaceBrowserPane(type, preferred, activeInstanceVersion.isBlank() ? "1.21.11" : activeInstanceVersion);
    }

    private String navStyle(boolean selected) {
        return "-fx-background-color:"+(selected?"#29384f":"transparent")+";-fx-background-radius:6;-fx-text-fill:"+(selected?"white":"#b7bdc8")+";-fx-font-size:13px;-fx-cursor:hand;";
    }
    private String cardStyle(){return "-fx-background-color:"+PANEL+";-fx-background-radius:7;-fx-border-color:#30343d;-fx-border-radius:7;";}
    private Label muted(String s){Label l=new Label(s);l.setWrapText(true);l.setStyle("-fx-text-fill:"+MUTED+";-fx-font-size:12px;");return l;}
    private Button small(String s){Button b=new Button(s);b.setStyle("-fx-background-color:"+ALT+";-fx-background-radius:4;-fx-text-fill:#e6e9ef;-fx-padding:8 12;-fx-cursor:hand;");return b;}
    private void styleCombo(ComboBox<String> c){c.setStyle("-fx-background-color:"+ALT+";-fx-mark-color:#d7dce5;-fx-font-size:12px;");}

    private void signIn() {
        String configuredClientId = preferences.get("microsoft.clientId", MicrosoftBrowserLogin.getClientId());
        if (configuredClientId == null || configuredClientId.isBlank() || configuredClientId.equals("YOUR_APPLICATION_CLIENT_ID")) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Microsoft application setup");
            dialog.setHeaderText("Configure Microsoft sign-in");
            dialog.setContentText("Paste a Microsoft Entra public desktop-app Application (client) ID. Relian uses browser sign-in with PKCE; never enter your Microsoft password or a client secret here.");
            dialog.initOwner(shell.getScene().getWindow());
            var answer = dialog.showAndWait();
            if (answer.isEmpty() || answer.get().trim().isBlank()) {
                info("Microsoft sign-in needs a valid public desktop application client ID. You can configure it later in Settings.");
                return;
            }
            configuredClientId = answer.get().trim();
            if (!configuredClientId.matches("(?i)[a-f0-9-]{36}")) {
                info("That does not look like a Microsoft application client ID. It should be a 36-character GUID. No account information was sent.");
                return;
            }
            preferences.put("microsoft.clientId", configuredClientId);
        }
        final String clientId = configuredClientId;
        accountLabel = "Opening Microsoft sign-in…";
        page(active);
        CompletableFuture.runAsync(() -> {
            try {
                MicrosoftBrowserLogin.LoginResult result = MicrosoftBrowserLogin.signIn(clientId);
                var verifiedSession = MinecraftAccountAuthenticator.authenticate(result.accessToken(), stage ->
                    javafx.application.Platform.runLater(() -> {
                        accountLabel = stage;
                        page(active);
                    }));
                javafx.application.Platform.runLater(() -> {
                    minecraftSession = verifiedSession;
                    offlineTestProfile = null;
                    accountLabel = verifiedSession.username();
                    page(active);
                    info("Minecraft account verified: " + verifiedSession.username() + ". You can now launch installed Vanilla versions.");
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    accountLabel = offlineTestProfile == null ? "Sign in with Microsoft"
                        : "Offline test: " + offlineTestProfile.username();
                    page(active);
                    info("Microsoft sign-in did not complete: " + ex.getMessage());
                });
            }
        });
    }

    private VBox home() {
        VBox wrap=new VBox(18), hero=new VBox(14); hero.setPadding(new Insets(28)); hero.setStyle(cardStyle());
        Label eyebrow=muted("MINECRAFT JAVA EDITION"), welcome=new Label("Ready to play?");
        welcome.setStyle("-fx-text-fill:"+FG+";-fx-font-size:28px;-fx-font-weight:bold;");
        hero.getChildren().addAll(eyebrow,welcome,muted("Choose an installation and launch Relian Client."));
        HBox controls=new HBox(10); controls.setAlignment(Pos.CENTER_LEFT);
        versions=new ComboBox<>(); versions.getItems().addAll(versionList); versions.setValue(activeInstanceVersion.isBlank() ? "1.21.11" : activeInstanceVersion); versions.setPrefWidth(170); styleCombo(versions);
        ComboBox<String> loader=new ComboBox<>(); loader.getItems().addAll("Fabric","Forge","NeoForge","Quilt","Vanilla"); loader.setValue(activeInstanceLoader.isBlank() ? "Vanilla" : activeInstanceLoader); loader.setPrefWidth(150); styleCombo(loader);
        Button play=new Button("PLAY"); play.setPrefSize(150,42);
        play.setStyle("-fx-background-color:"+BLUE+";-fx-background-radius:5;-fx-text-fill:white;-fx-font-weight:bold;-fx-cursor:hand;");
        Label playStatus=muted("Select an installed version. Microsoft sign-in and Minecraft ownership verification are required.");
        play.setOnAction(e->{
            if(minecraftSession==null){
                info(offlineTestProfile == null
                    ? "Sign in with Microsoft first. Relian verifies Xbox access and Minecraft Java ownership before launching."
                    : "The Offline / Local Test Profile is only for previewing the launcher UI. Sign in with a Microsoft account that owns Minecraft Java Edition to launch the game.");
                return;
            }
            String selectedVersion=versions.getValue();
            String selectedLoader=loader.getValue();
            String launchVersion=selectedVersion;
            if(activeInstanceDirectory!=null){
                if(!activeInstanceVersion.isBlank()&&!activeInstanceVersion.equals(selectedVersion)){
                    playStatus.setText("The selected instance uses Minecraft "+activeInstanceVersion+". Select that version before launching.");return;
                }
                if(!activeInstanceLoader.isBlank()&&!activeInstanceLoader.equals(selectedLoader)){
                    playStatus.setText("This instance is configured for "+activeInstanceLoader+". Select the matching loader or choose another instance.");return;
                }
                if(!"Vanilla".equals(selectedLoader)){
                    if(activeInstanceProfileId.isBlank()){
                        playStatus.setText("The "+selectedLoader+" loader has not been installed for this instance yet. Recreate or repair the instance to install its loader.");return;
                    }
                    launchVersion=activeInstanceProfileId;
                }
            }else if(!"Vanilla".equals(selectedLoader)){
                playStatus.setText("Create or select a "+selectedLoader+" instance first. Loaders are installed separately for each instance.");return;
            }
            final String launchProfileVersion=launchVersion;
            String javaPath=preferences.get("minecraft.java.path","");
            if(javaPath.isBlank()){playStatus.setText("Choose or install the compatible Java runtime in Settings first.");return;}
            java.nio.file.Path root=activeInstanceDirectory != null
                ? activeInstanceDirectory.toAbsolutePath().normalize()
                : (gameDirectory.isBlank()
                    ?java.nio.file.Path.of(System.getProperty("user.home"),".relian","game")
                    :java.nio.file.Path.of(gameDirectory).toAbsolutePath().normalize());
            play.setDisable(true); playStatus.setText("Starting Minecraft "+selectedVersion+"…");
            CompletableFuture.supplyAsync(()->{
                try{
                    return launchService.launch(root,launchProfileVersion,java.nio.file.Path.of(javaPath),minecraftSession,memoryLimitMb,
                        line->System.out.println(line));
                }catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((process,error)->javafx.application.Platform.runLater(()->{
                if(error!=null){
                    play.setDisable(false);
                    Throwable cause=error.getCause()==null?error:error.getCause();
                    playStatus.setText("Launch failed: "+cause.getMessage());
                }else{
                    playStatus.setText("Minecraft is running. Close the game to return to Relian.");
                    launchService.exitCode().whenComplete((code,exitError)->javafx.application.Platform.runLater(()->{
                        play.setDisable(false);
                        if(exitError!=null)playStatus.setText("Minecraft stopped. Exit status could not be read: "+exitError.getMessage());
                        else playStatus.setText("Minecraft closed with exit code "+code+". Check the launcher output if it did not reach the title screen.");
                    }));
                }
            }));
        });
        controls.getChildren().addAll(versions,loader,play); hero.getChildren().addAll(controls,playStatus);
        VBox offlineCard = new VBox(9);
        offlineCard.setPadding(new Insets(18));
        offlineCard.setStyle(cardStyle());
        Label offlineTitle = new Label("Offline / Local Test Profile");
        offlineTitle.setStyle("-fx-text-fill:" + FG + ";-fx-font-size:15px;-fx-font-weight:bold;");
        TextField offlineName = new TextField();
        offlineName.setPromptText("Test name (3–16 letters, numbers, underscore)");
        Label offlineStatus = muted("Use this to preview the launcher account UI without connecting an account. It is not a Minecraft login and cannot launch the game or access multiplayer.");
        Button useOffline = small("Use local test profile");
        Button clearOffline = small("Clear test profile");
        useOffline.setOnAction(event -> {
            try {
                offlineTestProfile = OfflineTestProfile.create(offlineName.getText());
                minecraftSession = null;
                accountLabel = "Offline test: " + offlineTestProfile.username();
                offlineStatus.setText("Local UI test profile active (" + offlineTestProfile.username()
                    + "). No Microsoft/Xbox/Minecraft ownership was verified. Game launch remains locked until Microsoft sign-in succeeds.");
                page(active);
            } catch (IllegalArgumentException ex) {
                offlineStatus.setText(ex.getMessage());
            }
        });
        clearOffline.setOnAction(event -> {
            offlineTestProfile = null;
            if (minecraftSession == null) accountLabel = "Sign in with Microsoft";
            offlineStatus.setText("Local test profile cleared. Microsoft sign-in is still required to launch Minecraft.");
            page(active);
        });
        HBox offlineActions = new HBox(8, useOffline, clearOffline);
        offlineCard.getChildren().addAll(offlineTitle,
            muted("For launcher development and UI testing only. This does not provide cracked access, bypass account checks, or create an authenticated Minecraft identity."),
            offlineName, offlineActions, offlineStatus);
        hero.getChildren().addAll(controls,playStatus,offlineCard);
        Button createInstance=small("＋ Create a new instance");
        createInstance.setOnAction(e->page("Instances"));
        hero.getChildren().add(createInstance);
        HBox cards=new HBox(12);
        cards.getChildren().addAll(card("MOD MANAGER","Browse mods and check compatibility.","Open Mods",()->page("Mods")),
            card("RESOURCE PACKS","Manage textures and visual styles.","Open Packs",()->page("Resource Packs")),
            card("SHADERS","Manage supported shader packs.","Open Shaders",()->page("Shaders")));
        wrap.getChildren().addAll(hero,muted("QUICK ACCESS"),cards); return wrap;
    }
    private VBox card(String t,String d,String action,Runnable r){
        VBox b=new VBox(10);b.setPadding(new Insets(17));b.setPrefWidth(230);b.setStyle(cardStyle());
        Label title=new Label(t);title.setStyle("-fx-text-fill:#aeb7c5;-fx-font-size:10px;-fx-font-weight:bold;");
        Button btn=small(action);btn.setOnAction(e->r.run());b.getChildren().addAll(title,muted(d),btn);return b;
    }
    private VBox versionPage(){
        VBox box=new VBox(12);
        box.getChildren().addAll(muted("Choose a Minecraft version. Download verified official client files into Relian's game directory."), 
            muted("Install the official client JAR, version metadata, required libraries, Windows native archives, and asset objects with SHA-1 verification."));
        for(String ver:versionList){HBox row=new HBox(12);row.setAlignment(Pos.CENTER_LEFT);row.setPadding(new Insets(14));row.setStyle(cardStyle());
            VBox info=new VBox(3);Label t=new Label("Minecraft "+ver);t.setStyle("-fx-text-fill:"+FG+";-fx-font-size:14px;-fx-font-weight:bold;");
            Label state=muted("Official release • client file checksum will be verified");
            info.getChildren().addAll(t,state);
            Region sp=new Region();HBox.setHgrow(sp,Priority.ALWAYS);
            Button select=small("Select");select.setOnAction(e->{page("Home");if(versions!=null)versions.setValue(ver);});
            Button install=small("Download client files");
            install.setOnAction(e->installVersionFiles(ver,install,state));
            row.getChildren().addAll(info,sp,select,install);box.getChildren().add(row);}
        return box;
    }

    private void installVersionFiles(String versionId,Button action,Label status){
        action.setDisable(true);status.setText("Preparing official Minecraft download…");
        java.nio.file.Path target=gameDirectory.isBlank()
            ?java.nio.file.Path.of(System.getProperty("user.home"),".relian","game")
            :java.nio.file.Path.of(gameDirectory).toAbsolutePath().normalize();
        CompletableFuture.supplyAsync(()->{
            try{
                var catalog=new com.relian.launcher.catalog.MinecraftVersionCatalog();
                var release=catalog.fetchAllReleases().stream().filter(item->item.id().equals(versionId)).findFirst()
                    .orElseThrow(()->new java.io.IOException("Minecraft "+versionId+" is no longer listed by Mojang."));
                return com.relian.launcher.catalog.MinecraftVersionInstaller.install(release,target,
                    message->javafx.application.Platform.runLater(()->status.setText(message)));
            }catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((result,error)->javafx.application.Platform.runLater(()->{
            action.setDisable(false);
            if(error!=null){
                Throwable cause=error.getCause()==null?error:error.getCause();
                status.setText("Download failed: "+cause.getMessage());
                return;
            }
            status.setText("Installed "+result.versionId()+": verified client JAR, "+result.libraries()+" libraries, "
                +result.nativeJars()+" Windows native archives, and "+result.assets()+" asset objects. Java setup and game launch are separate steps.");
        }));
    }
    private VBox manager(String title,String desc){
        VBox box=new VBox(14);TextField search=new TextField();search.setPromptText("Search "+title.toLowerCase()+"...");
        search.setStyle("-fx-control-inner-background:"+ALT+";-fx-text-fill:"+FG+";-fx-prompt-text-fill:#7f8794;");
        ComboBox<String> source=new ComboBox<>();source.getItems().addAll("All sources","Modrinth","CurseForge","Local files");source.setValue("All sources");styleCombo(source);
        HBox row=new HBox(10,search,source);HBox.setHgrow(search,Priority.ALWAYS);
        VBox empty=new VBox(8);empty.setPadding(new Insets(22));empty.setStyle(cardStyle());
        Label h=new Label(title+" library");h.setStyle("-fx-text-fill:"+FG+";-fx-font-size:16px;-fx-font-weight:bold;");
        empty.getChildren().addAll(h,muted(desc),muted("The download and installation service will be connected in a future build."));
        box.getChildren().addAll(muted(desc),row,empty);return box;
    }
    private VBox installedContentPage(){
        VBox page=new VBox(14);
        Label intro=muted("Manage mods, resource packs, and shaders in the selected Minecraft game directory. Toggle supported files on or off without deleting them.");
        Label location=muted(gameDirectory.isBlank()?"No game directory selected. Choose one in Settings first.":gameDirectory);
        ComboBox<String> category=new ComboBox<>();
        category.getItems().addAll("All content","Mods","Resource packs","Shaders");
        category.setValue("All content");
        styleCombo(category);
        VBox entries=new VBox(8);
        Label state=muted("Choose a game directory and refresh to scan installed content.");
        Button refresh=small("Refresh");
        Runnable scan=()->{
            entries.getChildren().clear();
            if(gameDirectory.isBlank()){
                state.setText("No game directory selected. Open Settings and choose your Minecraft folder.");
                return;
            }
            state.setText("Scanning installed content…");
            String selectedCategory=category.getValue();
            java.nio.file.Path root=java.nio.file.Path.of(gameDirectory).toAbsolutePath().normalize();
            var filter=switch(selectedCategory){
                case "Mods" -> com.relian.launcher.marketplace.InstalledContentManager.Category.MODS;
                case "Resource packs" -> com.relian.launcher.marketplace.InstalledContentManager.Category.RESOURCE_PACKS;
                case "Shaders" -> com.relian.launcher.marketplace.InstalledContentManager.Category.SHADERS;
                default -> com.relian.launcher.marketplace.InstalledContentManager.Category.ALL;
            };
            CompletableFuture.supplyAsync(()->{
                try{return com.relian.launcher.marketplace.InstalledContentManager.list(root,filter);}
                catch(java.io.IOException ex){throw new RuntimeException(ex);}
            }).whenComplete((found,error)->javafx.application.Platform.runLater(()->{
                entries.getChildren().clear();
                if(error!=null){
                    Throwable cause=error.getCause()==null?error:error.getCause();
                    state.setText("Could not scan the selected folder: "+cause.getMessage());return;
                }
                if(found.isEmpty()){state.setText("No installed files found in this category.");return;}
                long enabledCount=found.stream().filter(com.relian.launcher.marketplace.InstalledContentManager.Item::enabled).count();
                state.setText(found.size()+" file(s) found • "+enabledCount+" active • disabled files are preserved on disk.");
                for(var item:found){
                    java.nio.file.Path file=item.path();
                    HBox row=new HBox(10);row.setAlignment(Pos.CENTER_LEFT);row.setPadding(new Insets(12));row.setStyle(cardStyle());
                    VBox details=new VBox(3);
                    Label name=new Label(file.getFileName().toString());name.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
                    String size=item.sizeBytes()<1024*1024?(item.sizeBytes()/1024)+" KB":String.format(java.util.Locale.ROOT,"%.1f MB",item.sizeBytes()/(1024.0*1024.0));
                    String typeLabel=switch(item.kind()){case MOD->"Mod";case RESOURCE_PACK->"Resource pack";case SHADER->"Shader pack";};
                    details.getChildren().addAll(name,muted(typeLabel+" • "+(item.enabled()?"Enabled":"Disabled")+" • "+size));
                    Region push=new Region();HBox.setHgrow(push,Priority.ALWAYS);
                    Button toggle=small(item.enabled()?"Disable":"Enable");
                    toggle.setOnAction(event->{
                        toggle.setDisable(true);
                        try{
                            var updated=com.relian.launcher.marketplace.InstalledContentManager.toggleContent(root,file);
                            state.setText((item.enabled()?"Disabled ":"Enabled ")+updated.getFileName()+". The file contents were preserved.");
                            refresh.fire();
                        }catch(java.io.IOException ex){
                            toggle.setDisable(false);info("Could not change content state: "+ex.getMessage());
                        }
                    });
                    row.getChildren().addAll(details,push,toggle);
                    Button remove=small("Remove");
                    remove.setOnAction(event->{
                        Alert confirm=new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Remove installed content");confirm.setHeaderText("Remove "+file.getFileName()+"?");
                        confirm.setContentText("This permanently deletes this one file from "+file.getParent()+". Disabling a mod is reversible and does not require deletion.");
                        confirm.initOwner(shell.getScene().getWindow());
                        if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
                        try{
                            com.relian.launcher.marketplace.InstalledContentManager.delete(root,file);
                            refresh.fire();
                        }catch(java.io.IOException ex){info("Could not remove this file: "+ex.getMessage());}
                    });
                    row.getChildren().add(remove);entries.getChildren().add(row);
                }
            }));
        };
        refresh.setOnAction(e->scan.run());
        category.setOnAction(e->scan.run());
        HBox controls=new HBox(10,category,refresh);controls.setAlignment(Pos.CENTER_LEFT);
        page.getChildren().addAll(intro,location,controls,state,entries);
        if(!gameDirectory.isBlank()) scan.run();
        return page;
    }


    private java.nio.file.Path instancesRoot() {
        return gameDirectory.isBlank()
            ? java.nio.file.Path.of(System.getProperty("user.home"), ".relian", "instances")
            : java.nio.file.Path.of(gameDirectory).toAbsolutePath().normalize().resolve("RelianInstances");
    }

    private VBox existingInstancesPanel() {
        VBox panel=new VBox(10);
        panel.setPadding(new Insets(18));
        panel.setStyle(cardStyle());
        HBox heading=new HBox(10);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label title=new Label("Your instances");
        title.setStyle("-fx-text-fill:"+FG+";-fx-font-size:16px;-fx-font-weight:bold;");
        Region push=new Region(); HBox.setHgrow(push,Priority.ALWAYS);
        Button refresh=small("Refresh");
        heading.getChildren().addAll(title,push,refresh);
        VBox rows=new VBox(8);
        Label state=muted("Loading managed instances…");
        Runnable load=()->{
            rows.getChildren().clear();
            state.setText("Loading managed instances…");
            CompletableFuture.supplyAsync(()->{
                try{return com.relian.launcher.profiles.InstanceManager.list(instancesRoot());}
                catch(java.io.IOException ex){throw new RuntimeException(ex);}
            }).whenComplete((items,error)->javafx.application.Platform.runLater(()->{
                rows.getChildren().clear();
                if(error!=null){state.setText("Could not read instances: "+(error.getCause()==null?error.getMessage():error.getCause().getMessage()));return;}
                if(items.isEmpty()){state.setText("No Relian-managed instances yet. Create one below.");return;}
                state.setText(items.size()+" managed instance(s) • stored in "+instancesRoot());
                for(var item:items){
                    HBox row=new HBox(10);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setPadding(new Insets(12));
                    row.setStyle("-fx-background-color:"+ALT+";-fx-background-radius:6;");
                    VBox details=new VBox(3);
                    Label name=new Label(item.manifest().name());
                    name.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
                    String loader=item.manifest().loader()==null?"Unknown":item.manifest().loader();
                    details.getChildren().addAll(name,muted("Minecraft "+item.manifest().minecraftVersion()+" • "+loader+" • "+item.manifest().kind()));
                    Region spacer=new Region(); HBox.setHgrow(spacer,Priority.ALWAYS);
                    Button select=small("Use this instance");
                    select.setOnAction(event->{
                        activeInstanceDirectory=item.directory();
                        activeInstanceVersion=item.manifest().minecraftVersion();
                        activeInstanceLoader=item.manifest().loader()==null?"Vanilla":item.manifest().loader();
                        activeInstanceProfileId=readLoaderProfileId(activeInstanceDirectory);
                        preferences.put("active.instance.profileId",activeInstanceProfileId);
                        preferences.put("active.instance.path",activeInstanceDirectory.toString());
                        preferences.put("active.instance.version",activeInstanceVersion);
                        preferences.put("active.instance.loader",activeInstanceLoader);
                        state.setText("Active instance: "+item.manifest().name()+" • Minecraft "+activeInstanceVersion+" • "+activeInstanceLoader);
                        info("Selected "+item.manifest().name()+". Choose the same version on Home to launch this instance.");
                    });
                    Button open=small("Open folder");
                    open.setOnAction(event->{
                        try{
                            if(java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(item.directory().toFile());
                            else info("Instance folder: "+item.directory());
                        }catch(Exception ex){info("Could not open the instance folder: "+ex.getMessage());}
                    });
                    Button remove=small("Remove");
                    remove.setOnAction(event->{
                        Alert confirm=new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Remove instance");
                        confirm.setHeaderText("Remove "+item.manifest().name()+"?");
                        confirm.setContentText("This permanently deletes the selected Relian instance folder and its contents. It does not affect other instances or your default Minecraft folder.");
                        confirm.initOwner(shell.getScene().getWindow());
                        if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
                        try{
                            com.relian.launcher.profiles.InstanceManager.delete(instancesRoot(),item.directory());
                            refresh.fire();
                        }catch(Exception ex){info("Could not remove this instance: "+ex.getMessage());}
                    });
                    row.getChildren().addAll(details,spacer,select,open,remove);
                    rows.getChildren().add(row);
                }
            }));
        };
        refresh.setOnAction(event->load.run());
        refreshInstancesAction=load;
        panel.getChildren().addAll(heading,state,rows);
        load.run();
        return panel;
    }

    private VBox instancesPage() {
        VBox box=new VBox(14);
        box.getChildren().add(existingInstancesPanel());
        box.getChildren().add(muted("Create an isolated Minecraft installation. Vanilla stays completely unmodified; the other three choices include Relian's client-module set."));
        VBox panel=new VBox(12); panel.setPadding(new Insets(18)); panel.setStyle(cardStyle());
        Label nameLabel=new Label("Instance name"); nameLabel.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
        TextField name=new TextField(); name.setPromptText("My Minecraft instance");
        Label typeLabel=new Label("Choose how to create it"); typeLabel.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
        ComboBox<String> type=new ComboBox<>();
        type.getItems().addAll("Vanilla — clean Minecraft","Import — Prism / MultiMC / Lunar / Laby / Dawn / OneClient","Relian — Relian client + performance modules","Modpack — browse Modrinth modpacks");
        type.setValue("Vanilla — clean Minecraft"); type.setMaxWidth(Double.MAX_VALUE); styleCombo(type);
        ComboBox<String> version=new ComboBox<>(); version.getItems().setAll(versionList); version.setValue(versionList.contains("1.21.11")?"1.21.11":versionList.get(0)); styleCombo(version);
        ComboBox<String> loader=new ComboBox<>(); loader.getItems().addAll("Fabric","Forge","NeoForge","Quilt","Vanilla"); loader.setValue("Fabric"); styleCombo(loader);
        HBox versionRow=new HBox(10,version,loader); versionRow.setAlignment(Pos.CENTER_LEFT);
        Label rootLabel=muted("Instances will be stored in: "+(gameDirectory.isBlank()?System.getProperty("user.home")+"/.relian/instances":gameDirectory+"/RelianInstances"));
        Label importLabel=muted("No source instance selected.");
        java.nio.file.Path[] importPath={null};
        Button chooseImport=small("Choose existing instance folder");
        chooseImport.setOnAction(e->{
            DirectoryChooser chooser=new DirectoryChooser(); chooser.setTitle("Choose an existing Minecraft instance");
            File selected=chooser.showDialog(shell.getScene().getWindow());
            if(selected!=null){importPath[0]=selected.toPath();importLabel.setText("Import source: "+selected.getAbsolutePath());}
        });
        TextField modpackSearch=new TextField(); modpackSearch.setPromptText("Search Modrinth modpacks…");
        Button searchPacks=small("Search modpacks");
        javafx.scene.control.ListView<com.relian.launcher.marketplace.MarketplaceProject> packs=new javafx.scene.control.ListView<>();
        packs.setPrefHeight(170);
        packs.setCellFactory(view->new javafx.scene.control.ListCell<>(){
            @Override protected void updateItem(com.relian.launcher.marketplace.MarketplaceProject item,boolean empty){
                super.updateItem(item,empty);
                setText(empty||item==null?null:item.title()+"  •  "+item.author()+"  •  "+item.source());
            }
        });
        Label packStatus=muted("Choose a local Modrinth .mrpack file to import. Minecraft and loader versions are read from its manifest.");
        java.nio.file.Path[] mrpackPath={null};
        Button chooseMrpack=small("Choose local .mrpack file");
        chooseMrpack.setOnAction(e->{
            javafx.stage.FileChooser chooser=new javafx.stage.FileChooser();
            chooser.setTitle("Choose a Modrinth modpack archive");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Modrinth modpacks (*.mrpack)","*.mrpack"));
            File selected=chooser.showOpenDialog(shell.getScene().getWindow());
            if(selected!=null){
                mrpackPath[0]=selected.toPath();
                packStatus.setText("Selected "+selected.getName()+". The archive's Minecraft and loader metadata will override the selectors above.");
            }
        });
        searchPacks.setOnAction(e->{
            packStatus.setText("Searching Modrinth project pages…"); packs.getItems().clear();
            String query=modpackSearch.getText(), packVersion=version.getValue();
            CompletableFuture.supplyAsync(()->{
                try{return new com.relian.launcher.marketplace.ModrinthProvider().search(query,
                    com.relian.launcher.marketplace.ProjectType.MODPACK,packVersion,"",50);}
                catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((results,error)->javafx.application.Platform.runLater(()->{
                if(error!=null){packStatus.setText("Modpack search failed: "+error.getMessage());return;}
                packs.getItems().setAll(results); packStatus.setText(results.size()+" Modrinth modpack project(s) found. Open a project page to obtain its .mrpack archive, then select the file above.");
            }));
        });
        Button openPack=small("Open selected modpack page");
        openPack.setOnAction(e->{
            var selected=packs.getSelectionModel().getSelectedItem();
            if(selected==null){info("Select a modpack first.");return;}
            try{java.awt.Desktop.getDesktop().browse(selected.projectUrl());}
            catch(Exception ex){info("Could not open the modpack page: "+ex.getMessage());}
        });
        VBox importBox=new VBox(8,chooseImport,importLabel);
        VBox packBox=new VBox(8,chooseMrpack,packStatus,modpackSearch,searchPacks,packs,openPack);
        type.setOnAction(e->{
            boolean vanilla=type.getValue().startsWith("Vanilla");
            boolean modpack=type.getValue().startsWith("Modpack");
            importBox.setVisible(type.getValue().startsWith("Import")); importBox.setManaged(importBox.isVisible());
            packBox.setVisible(modpack); packBox.setManaged(packBox.isVisible());
            loader.setDisable(vanilla||modpack);
            version.setDisable(modpack);
        });
        loader.setDisable(true);
        importBox.setVisible(false); importBox.setManaged(false);
        packBox.setVisible(false); packBox.setManaged(false);
        Button create=small("Create instance");
        Label result=muted("Choose an option, then create your instance.");
        create.setOnAction(e->{
            String chosen=type.getValue();
            boolean importingModpack=chosen.startsWith("Modpack");
            var selectedPack=packs.getSelectionModel().getSelectedItem();
            if(importingModpack&&mrpackPath[0]==null){
                result.setText("Choose a local .mrpack archive first. The selected Modrinth project page can be used to obtain the archive.");
                return;
            }
            result.setText(importingModpack?"Validating and importing .mrpack into an isolated instance…":"Creating isolated instance…");
            create.setDisable(true);
            String selectedVersion=version.getValue(), selectedLoader=loader.getValue(), selectedName=name.getText();
            java.nio.file.Path selectedImport=importPath[0], selectedMrpack=mrpackPath[0];
            String selectedPackTitle=selectedPack==null?"":selectedPack.title();
            CompletableFuture.supplyAsync(()->{
                try{
                    java.nio.file.Path root=gameDirectory.isBlank()
                        ?java.nio.file.Path.of(System.getProperty("user.home"),".relian","instances")
                        :java.nio.file.Path.of(gameDirectory).resolve("RelianInstances");
                    var kind=chosen.startsWith("Vanilla")?com.relian.launcher.profiles.InstanceManager.Kind.VANILLA:
                        chosen.startsWith("Import")?com.relian.launcher.profiles.InstanceManager.Kind.IMPORT:
                        importingModpack?com.relian.launcher.profiles.InstanceManager.Kind.MODPACK:
                        com.relian.launcher.profiles.InstanceManager.Kind.RELIAN;
                    if(importingModpack){
                        return com.relian.launcher.marketplace.ModrinthModpackImporter.importPack(selectedMrpack,root,selectedName);
                    }
                    return com.relian.launcher.profiles.InstanceManager.create(root,selectedName,selectedVersion,selectedLoader,
                        kind,selectedImport,selectedPackTitle);
                }catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((created,error)->javafx.application.Platform.runLater(()->{
                create.setDisable(false);
                if(error!=null){
                    Throwable cause=error.getCause()==null?error:error.getCause();
                    result.setText("Could not create instance: "+cause.getMessage());
                    return;
                }
                activeInstanceDirectory=created.directory();
                activeInstanceVersion=created.manifest().minecraftVersion();
                activeInstanceLoader=created.manifest().loader()==null?"Vanilla":created.manifest().loader();
                activeInstanceProfileId="";
                preferences.remove("active.instance.profileId");
                preferences.put("active.instance.path",activeInstanceDirectory.toString());
                preferences.put("active.instance.version",activeInstanceVersion);
                preferences.put("active.instance.loader",activeInstanceLoader);
                refreshInstancesAction.run();
                String message="Created and selected "+created.manifest().name()+" at "+created.directory()+".";
                String actualVersion=created.manifest().minecraftVersion();
                if(importingModpack){
                    message+=" Imported the client-required files and safe overrides from the .mrpack. The Relian module manifest and performance defaults are included; the exact loader version is recorded in relian-modpack.json.";
                    result.setText(message+" Installing official Minecraft "+actualVersion+" base files…");
                    String importedLoader=created.manifest().loader()==null?"Vanilla":created.manifest().loader();
                    result.setText(message+" Installing official Minecraft files and "+importedLoader+" loader…");
                    installInstanceBaseFiles(created.directory(),actualVersion,result,message,importedLoader,readModpackLoaderVersion(created.directory()),false);
                    return;
                }
                String instanceLoader=chosen.startsWith("Vanilla")?"Vanilla":selectedLoader;
                message+=" Relian module manifest and Minecraft-only FPS video defaults are included.";
                result.setText(message+" Installing official Minecraft files and "+instanceLoader+" loader…");
                installInstanceBaseFiles(created.directory(),actualVersion,result,message,instanceLoader,"",
                    kindIncludesRelian(chosen)&&"Fabric".equals(instanceLoader));
            }));
        });
        panel.getChildren().addAll(nameLabel,name,typeLabel,type,versionRow,rootLabel,importBox,packBox,create,result);
        box.getChildren().addAll(panel,
            muted("Import copies supported game content such as mods, configs, resource packs, shaders, saves, and video options. It does not copy account tokens, launcher credentials, server lists, or unrelated launcher files."),
            muted("The .mrpack importer reads modrinth.index.json, installs required client files and safe overrides into a new isolated instance, verifies HTTPS downloads against SHA-1/SHA-512 hashes when supplied, and rejects traversal, symlinks, oversized entries, and unsafe destinations. Optional and server-only files are skipped."),
            muted("Minecraft and loader metadata are taken from the archive. Loader runtime installation and full modpack launch are not implemented yet; Relian performance modules are recorded but not injected into packs automatically to avoid conflicts."));
        return box;
    }


    private String readModpackLoaderVersion(java.nio.file.Path instanceDirectory) {
        java.nio.file.Path marker=instanceDirectory.resolve("relian-modpack.json");
        if(!java.nio.file.Files.isRegularFile(marker)||java.nio.file.Files.isSymbolicLink(marker))return "";
        try{
            var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(marker)).getAsJsonObject();
            String version=json.has("loaderVersion")?json.get("loaderVersion").getAsString():"";
            return version.matches("[0-9A-Za-z._+-]{1,64}")?version:"";
        }catch(Exception ignored){return "";}
    }

    private String readLoaderProfileId(java.nio.file.Path instanceDirectory) {
        java.nio.file.Path marker=instanceDirectory.resolve("relian-loader.json");
        if(!java.nio.file.Files.isRegularFile(marker)||java.nio.file.Files.isSymbolicLink(marker))return "";
        try{
            var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(marker)).getAsJsonObject();
            String profile=json.has("profileId")?json.get("profileId").getAsString():"";
            return profile.matches("[A-Za-z0-9._-]{1,96}")?profile:"";
        }catch(Exception ignored){return "";}
    }

    private record InstanceInstallOutcome(com.relian.launcher.catalog.MinecraftVersionInstaller.InstallResult base,
                                          com.relian.launcher.catalog.LoaderInstaller.Result loader) {}

    private java.nio.file.Path compatibleJavaFor(String minecraftVersion) throws Exception {
        int required=com.relian.launcher.runtime.JavaRuntimeManager.requiredMajor(minecraftVersion);
        String saved=preferences.get("minecraft.java.path","");
        if(!saved.isBlank()){
            java.nio.file.Path candidate=java.nio.file.Path.of(saved).toAbsolutePath().normalize();
            if(java.nio.file.Files.isRegularFile(candidate)){
                Process check=new ProcessBuilder(candidate.toString(),"-version").redirectErrorStream(true).start();
                if(check.waitFor(5,java.util.concurrent.TimeUnit.SECONDS)){
                    String output=new String(check.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(?:version|openjdk)[^0-9]*([0-9]+)(?:[.]([0-9]+))?").matcher(output);
                    if(matcher.find()){
                        int first=Integer.parseInt(matcher.group(1));
                        int major=first==1&&matcher.group(2)!=null?Integer.parseInt(matcher.group(2)):first;
                        if(major==required)return candidate;
                    }
                }else check.destroyForcibly();
            }
        }
        var detected=com.relian.launcher.runtime.JavaRuntimeManager.findCompatible(
            com.relian.launcher.runtime.JavaRuntimeManager.detectInstalled(),minecraftVersion);
        if(detected!=null){preferences.put("minecraft.java.path",detected.executable().toString());return detected.executable();}
        java.nio.file.Path runtimeRoot=gameDirectory.isBlank()
            ?java.nio.file.Path.of(System.getProperty("user.home"),".relian","runtimes")
            :java.nio.file.Path.of(gameDirectory).resolve("runtimes");
        java.nio.file.Path installed=com.relian.launcher.runtime.TemurinRuntimeInstaller.installWindows(required,runtimeRoot);
        preferences.put("minecraft.java.path",installed.toString());
        return installed;
    }

    private void installInstanceBaseFiles(java.nio.file.Path instanceDirectory,String version,Label status,
                                          String summary,String loader,String requestedLoaderVersion,boolean addRelianPerformanceMods){
        CompletableFuture.supplyAsync(()->{
            try{
                var catalog=new com.relian.launcher.catalog.MinecraftVersionCatalog();
                var release=catalog.fetchAllReleases().stream().filter(item->item.id().equals(version)).findFirst()
                    .orElseThrow(()->new java.io.IOException("Minecraft "+version+" is not available in Mojang's release manifest."));
                var base=com.relian.launcher.catalog.MinecraftVersionInstaller.install(release,instanceDirectory,
                    message->javafx.application.Platform.runLater(()->status.setText(message)));
                com.relian.launcher.catalog.LoaderInstaller.Result loaderResult=null;
                if(loader!=null&&!loader.equals("Vanilla")){
                    java.nio.file.Path javaExecutable=compatibleJavaFor(version);
                    loaderResult=com.relian.launcher.catalog.LoaderInstaller.install(loader,version,requestedLoaderVersion,instanceDirectory,javaExecutable,
                        message->javafx.application.Platform.runLater(()->status.setText(message)));
                    var marker=new java.util.LinkedHashMap<String,String>();
                    marker.put("loader",loader);marker.put("minecraftVersion",version);
                    marker.put("loaderVersion",loaderResult.loaderVersion());marker.put("profileId",loaderResult.profileId());
                    java.nio.file.Files.writeString(instanceDirectory.resolve("relian-loader.json"),
                        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(marker));
                }
                return new InstanceInstallOutcome(base,loaderResult);
            }catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((installed,error)->javafx.application.Platform.runLater(()->{
            if(error!=null){Throwable cause=error.getCause()==null?error:error.getCause();status.setText(summary+" Installation failed: "+cause.getMessage());return;}
            if(installed.loader()!=null){
                activeInstanceProfileId=installed.loader().profileId();
                activeInstanceLoader=installed.loader().loader();
                preferences.put("active.instance.profileId",activeInstanceProfileId);
                preferences.put("active.instance.loader",activeInstanceLoader);
                status.setText(summary+" Installed Minecraft "+installed.base().versionId()+" and "+installed.loader().loader()
                    +" "+installed.loader().loaderVersion()+". Ready to launch profile "+installed.loader().profileId()+".");
                if(addRelianPerformanceMods&&"Fabric".equals(installed.loader().loader()))
                    installRelianPerformanceMods(instanceDirectory,version,status,summary+" Fabric loader installed.");
            }else{
                activeInstanceProfileId="";
                preferences.remove("active.instance.profileId");
                status.setText(summary+" Installed Minecraft "+installed.base().versionId()+" with "+installed.base().libraries()
                    +" libraries, "+installed.base().nativeJars()+" Windows native archives, and "+installed.base().assets()
                    +" assets. Vanilla instance is ready to launch.");
            }
        }));
    }

    private boolean kindIncludesRelian(String chosen){return !chosen.startsWith("Vanilla");}

    private void installRelianPerformanceMods(java.nio.file.Path directory,String gameVersion,Label status,String summary){
        CompletableFuture.runAsync(()->{
            java.util.List<String> installed=new java.util.ArrayList<>(), skipped=new java.util.ArrayList<>();
            String[][] modules={{"sodium","Sodium"},{"lithium","Lithium"},{"ferrite-core","FerriteCore"},
                {"immediatelyfast","ImmediatelyFast"},{"entityculling","EntityCulling"},{"dynamic-fps","Dynamic FPS"}};
            for(String[] module:modules){
                try{
                    java.nio.file.Path modsDir=directory.resolve("mods");
                    boolean alreadyPresent=false;
                    if(java.nio.file.Files.isDirectory(modsDir)){
                        try(var files=java.nio.file.Files.list(modsDir)){
                            alreadyPresent=files.anyMatch(file->java.nio.file.Files.isRegularFile(file)
                                && file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).startsWith(module[0]+"-")
                                && file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
                        }
                    }
                    if(alreadyPresent){installed.add(module[1]+" (already present)");continue;}
                    var project=new com.relian.launcher.marketplace.MarketplaceProject(module[0],module[1],"Relian",
                        "Relian recommended performance module",com.relian.launcher.marketplace.ProjectType.MOD,
                        java.net.URI.create("https://modrinth.com/mod/"+module[0]),java.util.List.of("fabric"),"Modrinth");
                    com.relian.launcher.marketplace.ModrinthInstaller.install(project,gameVersion,"Fabric",directory);
                    installed.add(module[1]);
                }catch(Exception ex){skipped.add(module[1]);}
            }
            try{com.relian.launcher.profiles.InstanceManager.updateRelianModuleStatus(directory,installed,skipped);}
            catch(Exception ignored){}
            javafx.application.Platform.runLater(()->status.setText(summary+" Installed or already present: "+(installed.isEmpty()?"none":String.join(", ",installed))+
                ". Not available for this version: "+(skipped.isEmpty()?"none":String.join(", ",skipped))+
                ". These mods are installed into the selected Fabric instance."));
        });
    }

    private VBox settings(){
        VBox b=new VBox(14);

        VBox appearance=new VBox(10);
        appearance.setPadding(new Insets(18)); appearance.setStyle(cardStyle());
        Label appearanceTitle=new Label("Launcher appearance");
        appearanceTitle.setStyle("-fx-text-fill:"+FG+";-fx-font-size:15px;-fx-font-weight:bold;");
        ColorPicker backgroundPicker=new ColorPicker(Color.web(backgroundColor));
        Button applyBackground=small("Apply background color");
        applyBackground.setOnAction(e->{
            backgroundColor=toHex(backgroundPicker.getValue());
            preferences.put("background.color",backgroundColor);
            page("Settings");
        });
        appearance.getChildren().addAll(appearanceTitle,muted("Choose a solid launcher background. This uses a simple color fill rather than animated effects, keeping GPU use low."),backgroundPicker,applyBackground);

        VBox performance=new VBox(10);
        performance.setPadding(new Insets(18)); performance.setStyle(cardStyle());
        Label performanceTitle=new Label("Minecraft memory limit");
        performanceTitle.setStyle("-fx-text-fill:"+FG+";-fx-font-size:15px;-fx-font-weight:bold;");
        Label memoryValue=muted("Maximum Minecraft memory: "+memoryLimitMb+" MB");
        Slider memorySlider=new Slider(1024,6144,memoryLimitMb);
        memorySlider.setMajorTickUnit(1024); memorySlider.setMinorTickCount(3); memorySlider.setSnapToTicks(true); memorySlider.setShowTickMarks(true);
        memorySlider.valueProperty().addListener((obs,oldValue,newValue)->memoryValue.setText("Maximum Minecraft memory: "+((int)Math.round(newValue.doubleValue()/256.0)*256)+" MB"));
        Button saveMemory=small("Save memory limit");
        saveMemory.setOnAction(e->{
            memoryLimitMb=(int)Math.round(memorySlider.getValue()/256.0)*256;
            preferences.putInt("memory.maxMb",memoryLimitMb);
            memoryValue.setText("Saved: "+memoryLimitMb+" MB for future Minecraft launches");
        });
        performance.getChildren().addAll(performanceTitle,muted("This setting is for the Minecraft game process, not the Relian launcher. Start with 2–4 GB; avoid assigning nearly all your computer's RAM."),memorySlider,memoryValue,saveMemory);
        VBox fpsTools=new VBox(8); fpsTools.setPadding(new Insets(16)); fpsTools.setStyle(cardStyle());
        Label fpsTitle=new Label("Minecraft FPS profile"); fpsTitle.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
        Label fpsTarget=muted("Choose a specific instance folder to tune. These settings change only that Minecraft instance's options.txt.");
        ComboBox<String> fpsPreset=new ComboBox<>(); fpsPreset.getItems().addAll("FPS boost","Balanced","Keep current settings"); fpsPreset.setValue("FPS boost"); styleCombo(fpsPreset);
        java.nio.file.Path[] fpsDirectory={null};
        Button chooseFpsFolder=small("Choose instance folder");
        chooseFpsFolder.setOnAction(e->{
            DirectoryChooser chooser=new DirectoryChooser(); chooser.setTitle("Choose a Minecraft instance folder");
            File selected=chooser.showDialog(shell.getScene().getWindow());
            if(selected!=null){fpsDirectory[0]=selected.toPath();fpsTarget.setText("Selected instance: "+selected.getAbsolutePath());}
        });
        Button applyFps=small("Apply FPS profile");
        applyFps.setOnAction(e->{
            if(fpsDirectory[0]==null){info("Choose an instance folder first.");return;}
            var preset=fpsPreset.getValue().equals("FPS boost")?com.relian.launcher.settings.MinecraftPerformanceProfile.Preset.FPS_BOOST:
                fpsPreset.getValue().equals("Balanced")?com.relian.launcher.settings.MinecraftPerformanceProfile.Preset.BALANCED:
                com.relian.launcher.settings.MinecraftPerformanceProfile.Preset.ORIGINAL;
            try{
                com.relian.launcher.settings.MinecraftPerformanceProfile.apply(fpsDirectory[0],preset);
                fpsTarget.setText("Applied "+fpsPreset.getValue()+" to "+fpsDirectory[0]+". Restart Minecraft to see the changes.");
            }catch(Exception ex){fpsTarget.setText("Could not apply the profile: "+ex.getMessage());}
        });
        fpsTools.getChildren().addAll(fpsTitle,muted("FPS Boost lowers Minecraft-only view distance, particles, clouds, biome blending, and entity rendering cost. It does not cap or change Windows CPU, GPU, or RAM availability for other applications."),fpsPreset,chooseFpsFolder,applyFps,fpsTarget);


        VBox directories=new VBox(10);
        directories.setPadding(new Insets(18)); directories.setStyle(cardStyle());
        Label directoryTitle=new Label("Minecraft game directory");
        directoryTitle.setStyle("-fx-text-fill:"+FG+";-fx-font-size:15px;-fx-font-weight:bold;");
        Label directoryValue=muted(gameDirectory.isBlank()?"Not selected — the default Minecraft directory will be used until profile support is ready.":gameDirectory);
        Button chooseDirectory=small("Choose folder");
        chooseDirectory.setOnAction(e->{
            DirectoryChooser chooser=new DirectoryChooser(); chooser.setTitle("Choose Minecraft game directory");
            File initial=gameDirectory.isBlank()?null:new File(gameDirectory);
            if(initial!=null && initial.isDirectory()) chooser.setInitialDirectory(initial);
            File selected=chooser.showDialog(shell.getScene().getWindow());
            if(selected!=null){gameDirectory=selected.getAbsolutePath();preferences.put("game.directory",gameDirectory);directoryValue.setText(gameDirectory);}
        });
        directories.getChildren().addAll(directoryTitle,muted("Choose where Relian should store game files. Separate per-version profiles will be added with the game installer."),directoryValue,chooseDirectory);

        VBox runtime=new VBox(8); runtime.setPadding(new Insets(16)); runtime.setStyle(cardStyle());
        Label javaTitle=new Label("Minecraft Java runtime"); javaTitle.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
        Label javaStatus=muted("Detect installed Java versions. Minecraft versions require different Java major versions.");
        ComboBox<String> javaChoices=new ComboBox<>(); javaChoices.setPrefWidth(650); styleCombo(javaChoices);
        Button installJava=small("Download & install compatible Java");
        installJava.setOnAction(event->{
            String selectedVersion=versions==null || versions.getValue()==null?"1.21.1":versions.getValue();
            int required=com.relian.launcher.runtime.JavaRuntimeManager.requiredMajor(selectedVersion);
            javaStatus.setText("Downloading and verifying Eclipse Temurin Java "+required+"…");
            installJava.setDisable(true);
            CompletableFuture.supplyAsync(()->{
                try{
                    java.nio.file.Path root=gameDirectory.isBlank()
                        ?java.nio.file.Path.of(System.getProperty("user.home"),".relian","runtimes")
                        :java.nio.file.Path.of(gameDirectory).resolve("runtimes");
                    return com.relian.launcher.runtime.TemurinRuntimeInstaller.installWindows(required,root);
                }catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((executable,error)->javafx.application.Platform.runLater(()->{
                installJava.setDisable(false);
                if(error!=null){
                    Throwable cause=error.getCause()==null?error:error.getCause();
                    javaStatus.setText("Java installation failed: "+cause.getMessage());
                    return;
                }
                preferences.put("minecraft.java.path",executable.toString());
                javaStatus.setText("Installed and checksum-verified Java "+required+".");
                javaChoices.getItems().add("Java "+required+" — "+executable);
                javaChoices.setValue("Java "+required+" — "+executable);
            }));
        });
        Button detectJava=small("Detect installed Java");
        detectJava.setOnAction(event->{
            detectJava.setDisable(true); javaStatus.setText("Scanning JAVA_HOME, PATH, and common Windows Java folders…");
            CompletableFuture.supplyAsync(()->com.relian.launcher.runtime.JavaRuntimeManager.detectInstalled())
                .whenComplete((found,error)->javafx.application.Platform.runLater(()->{
                    detectJava.setDisable(false); javaChoices.getItems().clear();
                    if(error!=null){javaStatus.setText("Java detection failed: "+error.getMessage());return;}
                    for(var item:found) javaChoices.getItems().add(item.toString());
                    String selectedPath=preferences.get("minecraft.java.path","");
                    int required=com.relian.launcher.runtime.JavaRuntimeManager.requiredMajor(versions==null?"1.21.1":versions.getValue());
                    var compatible=com.relian.launcher.runtime.JavaRuntimeManager.findCompatible(found,versions==null?"1.21.1":versions.getValue());
                    if(!selectedPath.isBlank()){
                        for(var item:found) if(item.executable().toString().equals(selectedPath)) javaChoices.setValue(item.toString());
                    }
                    if(javaChoices.getValue()==null && compatible!=null) javaChoices.setValue(compatible.toString());
                    javaStatus.setText(found.isEmpty()?"No Java installation was detected. Runtime downloads are the next step."
                        :found.size()+" Java installation(s) found. Selected Minecraft version needs Java "+required+". "
                        +(compatible==null?"No exact compatible runtime was found.":"A compatible runtime is available."));
                }));
        });
        javaChoices.setOnAction(event->{
            String selected=javaChoices.getValue();
            if(selected!=null){
                int split=selected.indexOf(" — ");
                if(split>=0) preferences.put("minecraft.java.path",selected.substring(split+3));
            }
        });
        runtime.getChildren().addAll(javaTitle,muted("Detect existing Java installations or download a checksum-verified Eclipse Temurin runtime for the selected Minecraft version."),detectJava,installJava,javaChoices,javaStatus);
        VBox account=new VBox(8); account.setPadding(new Insets(16)); account.setStyle(cardStyle());
        Label accountTitle=new Label("Microsoft account connection"); accountTitle.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
        TextField clientIdField=new TextField(preferences.get("microsoft.clientId", MicrosoftBrowserLogin.getClientId()));
        clientIdField.setPromptText("Microsoft public desktop application client ID (GUID)");
        Button saveClientId=small("Save Microsoft application ID");
        Label accountHelp=muted("Required setup: register Relian as a public/native desktop app for personal Microsoft accounts, enable authorization-code flow with PKCE, add the http://localhost mobile/desktop redirect, and configure the XboxLive.signin scope. Relian opens the system browser and never asks for your Microsoft password.");
        Button openRegistration=small("Open Microsoft app registrations");
        openRegistration.setOnAction(event->{
            try{java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://entra.microsoft.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade"));}
            catch(Exception ex){accountHelp.setText("Open https://entra.microsoft.com and choose App registrations.");}
        });
        saveClientId.setOnAction(event->{
            String value=clientIdField.getText()==null?"":clientIdField.getText().trim();
            if(!value.matches("(?i)[a-f0-9-]{8}-[a-f0-9-]{4}-[a-f0-9-]{4}-[a-f0-9-]{4}-[a-f0-9-]{12}")){accountHelp.setText("Enter the Application (client) ID in GUID format. No Microsoft credentials were sent.");return;}
            preferences.put("microsoft.clientId",value);
            accountHelp.setText("Public application ID saved locally. Return to Home and select Sign in with Microsoft. The app registration must also have the XboxLive.signin permission and localhost redirect.");
        });
        account.getChildren().addAll(accountTitle,clientIdField,saveClientId,openRegistration,accountHelp);
        b.getChildren().addAll(appearance,performance,fpsTools,directories,runtime,account);
        return b;
    }

    private static String toHex(Color color){
        return String.format("#%02X%02X%02X",Math.round(color.getRed()*255),Math.round(color.getGreen()*255),Math.round(color.getBlue()*255));
    }
    private void info(String message){Alert a=new Alert(Alert.AlertType.INFORMATION);a.setTitle("Relian Client");a.setHeaderText("Not connected yet");a.setContentText(message);a.showAndWait();}
    public static void main(String[] args){launch(args);}
}
