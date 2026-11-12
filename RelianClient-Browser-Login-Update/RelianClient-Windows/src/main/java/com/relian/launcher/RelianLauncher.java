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
    private final BorderPane shell = new BorderPane();
    private final StackPane host = new StackPane();
    private ComboBox<String> versions;
    private String active = "Home";
    private String accountLabel = "Sign in with Microsoft";
    private final List<String> versionList = new ArrayList<>(List.of("26.3","26.2","1.21.11","1.21.10","1.20.6","1.20.1","1.19.4","1.18.2","1.8.9","1.8.8"));

    @Override public void start(Stage stage) {
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
        for(String s:List.of("Home","Versions","Mods","Modpacks","Resource Packs","Shaders","Installed Content","Settings")) {
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
        java.nio.file.Path preferred = gameDirectory.isBlank() ? null : java.nio.file.Path.of(gameDirectory);
        return new com.relian.launcher.marketplace.MarketplaceBrowserPane(type, preferred);
    }

    private String navStyle(boolean selected) {
        return "-fx-background-color:"+(selected?"#29384f":"transparent")+";-fx-background-radius:6;-fx-text-fill:"+(selected?"white":"#b7bdc8")+";-fx-font-size:13px;-fx-cursor:hand;";
    }
    private String cardStyle(){return "-fx-background-color:"+PANEL+";-fx-background-radius:7;-fx-border-color:#30343d;-fx-border-radius:7;";}
    private Label muted(String s){Label l=new Label(s);l.setWrapText(true);l.setStyle("-fx-text-fill:"+MUTED+";-fx-font-size:12px;");return l;}
    private Button small(String s){Button b=new Button(s);b.setStyle("-fx-background-color:"+ALT+";-fx-background-radius:4;-fx-text-fill:#e6e9ef;-fx-padding:8 12;-fx-cursor:hand;");return b;}
    private void styleCombo(ComboBox<String> c){c.setStyle("-fx-background-color:"+ALT+";-fx-mark-color:#d7dce5;-fx-font-size:12px;");}

    private void signIn() {
        String clientId = MicrosoftBrowserLogin.getClientId();
        if (clientId == null || clientId.isBlank() || clientId.equals("YOUR_APPLICATION_CLIENT_ID")) {
            info("Browser sign-in is wired into Relian, but it needs the launcher's Microsoft application ID. The developer must register Relian as a public desktop application and put that ID in auth.properties. You do not need to give Relian your Microsoft password.");
            return;
        }
        accountLabel = "Opening Microsoft sign-in…";
        page(active);
        CompletableFuture.runAsync(() -> {
            try {
                MicrosoftBrowserLogin.LoginResult result = MicrosoftBrowserLogin.signIn(clientId);
                javafx.application.Platform.runLater(() -> {
                    accountLabel = "Microsoft account connected";
                    page(active);
                    info("Microsoft browser sign-in completed. Minecraft ownership and Xbox authentication still need to be verified before the Play button can launch the game. Your access token is kept in memory only for this session.");
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    accountLabel = "Sign in with Microsoft";
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
        versions=new ComboBox<>(); versions.getItems().addAll(versionList); versions.setValue("1.21.11"); versions.setPrefWidth(170); styleCombo(versions);
        ComboBox<String> loader=new ComboBox<>(); loader.getItems().addAll("Fabric","Forge","NeoForge","Quilt","Vanilla"); loader.setValue("Fabric"); loader.setPrefWidth(150); styleCombo(loader);
        Button play=new Button("PLAY"); play.setPrefSize(150,42);
        play.setStyle("-fx-background-color:"+BLUE+";-fx-background-radius:5;-fx-text-fill:white;-fx-font-weight:bold;-fx-cursor:hand;");
        play.setOnAction(e->info("Minecraft launching is not connected yet. This is the first launcher prototype."));
        controls.getChildren().addAll(versions,loader,play); hero.getChildren().add(controls);
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
        VBox box=new VBox(12);box.getChildren().add(muted("Choose a Minecraft version for a separate installation profile."));
        for(String ver:versionList){HBox row=new HBox(12);row.setAlignment(Pos.CENTER_LEFT);row.setPadding(new Insets(14));row.setStyle(cardStyle());
            VBox info=new VBox(3);Label t=new Label("Minecraft "+ver);t.setStyle("-fx-text-fill:"+FG+";-fx-font-size:14px;-fx-font-weight:bold;");
            info.getChildren().addAll(t,muted("Version target • compatibility checked during installation"));
            Region sp=new Region();HBox.setHgrow(sp,Priority.ALWAYS);Button select=small("Select");
            select.setOnAction(e->{page("Home");if(versions!=null)versions.setValue(ver);});row.getChildren().addAll(info,sp,select);box.getChildren().add(row);}
        return box;
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
        Label intro=muted("Review content installed by Relian in your selected Minecraft game directory. Only individual files in the standard mods, resourcepacks, and shaderpacks folders are shown.");
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
            CompletableFuture.supplyAsync(()->{
                java.util.List<java.nio.file.Path> found=new java.util.ArrayList<>();
                java.util.Map<String,String> folders=new java.util.LinkedHashMap<>();
                if("All content".equals(selectedCategory)||"Mods".equals(selectedCategory)) folders.put("mods",".jar");
                if("All content".equals(selectedCategory)||"Resource packs".equals(selectedCategory)) folders.put("resourcepacks",".zip");
                if("All content".equals(selectedCategory)||"Shaders".equals(selectedCategory)) folders.put("shaderpacks",".zip");
                for(var folder:folders.entrySet()){
                    java.nio.file.Path dir=root.resolve(folder.getKey()).normalize();
                    if(!dir.startsWith(root)||!java.nio.file.Files.isDirectory(dir,java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                    try(var stream=java.nio.file.Files.newDirectoryStream(dir)){
                        for(java.nio.file.Path file:stream){
                            if(java.nio.file.Files.isRegularFile(file,java.nio.file.LinkOption.NOFOLLOW_LINKS)
                                    && !java.nio.file.Files.isSymbolicLink(file)
                                    && file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(folder.getValue())){
                                found.add(file.toAbsolutePath().normalize());
                            }
                        }
                    }catch(java.io.IOException ignored){}
                }
                found.sort(java.util.Comparator.comparing(p->p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)));
                return found;
            }).whenComplete((found,error)->javafx.application.Platform.runLater(()->{
                entries.getChildren().clear();
                if(error!=null){state.setText("Could not scan the selected folder: "+error.getMessage());return;}
                if(found.isEmpty()){state.setText("No installed files found in this category.");return;}
                state.setText(found.size()+" installed file(s) found. Removing a file only deletes it from this selected folder.");
                for(java.nio.file.Path file:found){
                    HBox row=new HBox(10);row.setAlignment(Pos.CENTER_LEFT);row.setPadding(new Insets(12));row.setStyle(cardStyle());
                    VBox details=new VBox(3);
                    Label name=new Label(file.getFileName().toString());name.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
                    String size;
                    try{long bytes=java.nio.file.Files.size(file);size=bytes<1024*1024?(bytes/1024)+" KB":String.format(java.util.Locale.ROOT,"%.1f MB",bytes/(1024.0*1024.0));}
                    catch(java.io.IOException ex){size="Size unavailable";}
                    details.getChildren().addAll(name,muted(file.getParent().getFileName()+" • "+size));
                    Region push=new Region();HBox.setHgrow(push,Priority.ALWAYS);
                    Button remove=small("Remove");
                    remove.setOnAction(event->{
                        Alert confirm=new Alert(Alert.AlertType.CONFIRMATION);
                        confirm.setTitle("Remove installed content");confirm.setHeaderText("Remove "+file.getFileName()+"?");
                        confirm.setContentText("This deletes this one file from "+file.getParent()+". It does not uninstall dependencies or modify other files.");
                        confirm.initOwner(shell.getScene().getWindow());
                        if(confirm.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
                        try{
                            java.nio.file.Path parent=file.getParent().toRealPath();
                            java.nio.file.Path expected=root.resolve(parent.getFileName()).toAbsolutePath().normalize();
                            if(!parent.equals(expected)||!java.nio.file.Files.isRegularFile(file,java.nio.file.LinkOption.NOFOLLOW_LINKS)
                                    ||java.nio.file.Files.isSymbolicLink(file)) throw new java.io.IOException("The file is no longer a safe removable file in the selected content folder.");
                            String n=file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                            if(!(n.endsWith(".jar")||n.endsWith(".zip"))) throw new java.io.IOException("Unsupported file type.");
                            java.nio.file.Files.delete(file);
                            refresh.fire();
                        }catch(java.io.IOException ex){info("Could not remove this file: "+ex.getMessage());}
                    });
                    row.getChildren().addAll(details,push,remove);entries.getChildren().add(row);
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

        VBox runtime=new VBox(4); runtime.setPadding(new Insets(16)); runtime.setStyle(cardStyle());
        Label javaTitle=new Label("Java runtime"); javaTitle.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
        runtime.getChildren().addAll(javaTitle,muted("Relian's launcher uses Java 21. Per-version Minecraft Java runtime selection is not connected yet."));
        b.getChildren().addAll(appearance,performance,directories,runtime);
        return b;
    }

    private static String toHex(Color color){
        return String.format("#%02X%02X%02X",Math.round(color.getRed()*255),Math.round(color.getGreen()*255),Math.round(color.getBlue()*255));
    }
    private void info(String message){Alert a=new Alert(Alert.AlertType.INFORMATION);a.setTitle("Relian Client");a.setHeaderText("Not connected yet");a.setContentText(message);a.showAndWait();}
    public static void main(String[] args){launch(args);}
}
