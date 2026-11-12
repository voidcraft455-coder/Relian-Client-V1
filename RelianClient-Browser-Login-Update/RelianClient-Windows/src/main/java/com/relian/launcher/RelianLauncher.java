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


    private VBox instancesPage() {
        VBox box=new VBox(14);
        box.getChildren().add(muted("Create an isolated Minecraft installation. Vanilla stays completely unmodified; the other three choices include Relian's client-module set."));
        VBox panel=new VBox(12); panel.setPadding(new Insets(18)); panel.setStyle(cardStyle());
        Label nameLabel=new Label("Instance name"); nameLabel.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
        TextField name=new TextField(); name.setPromptText("My Minecraft instance");
        Label typeLabel=new Label("Choose how to create it"); typeLabel.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
        ComboBox<String> type=new ComboBox<>();
        type.getItems().addAll("Vanilla — clean Minecraft","Import — Prism / MultiMC / Lunar / Laby / Dawn / OneClient","Relian — Relian client + performance modules","Modpack — browse Modrinth modpacks");
        type.setValue("Relian — Relian client + performance modules"); type.setMaxWidth(Double.MAX_VALUE); styleCombo(type);
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
        Label packStatus=muted("Search Modrinth to browse available modpacks.");
        searchPacks.setOnAction(e->{
            packStatus.setText("Searching Modrinth…"); packs.getItems().clear();
            String query=modpackSearch.getText(), packVersion=version.getValue();
            CompletableFuture.supplyAsync(()->{
                try{return new com.relian.launcher.marketplace.ModrinthProvider().search(query,
                    com.relian.launcher.marketplace.ProjectType.MODPACK,packVersion,"",50);}
                catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((results,error)->javafx.application.Platform.runLater(()->{
                if(error!=null){packStatus.setText("Modpack search failed: "+error.getMessage());return;}
                packs.getItems().setAll(results); packStatus.setText(results.size()+" modpack(s) found. Select one to create its instance profile.");
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
        VBox packBox=new VBox(8,modpackSearch,searchPacks,packs,packStatus,openPack);
        type.setOnAction(e->{
            importBox.setVisible(type.getValue().startsWith("Import")); importBox.setManaged(importBox.isVisible());
            packBox.setVisible(type.getValue().startsWith("Modpack")); packBox.setManaged(packBox.isVisible());
            loader.setDisable(type.getValue().startsWith("Vanilla"));
        });
        importBox.setVisible(false); importBox.setManaged(false);
        packBox.setVisible(false); packBox.setManaged(false);
        Button create=small("Create instance");
        Label result=muted("Choose an option, then create your instance.");
        create.setOnAction(e->{
            String chosen=type.getValue(); var selectedPack=packs.getSelectionModel().getSelectedItem();
            if(chosen.startsWith("Modpack")&&selectedPack==null){result.setText("Choose a modpack from the results first.");return;}
            result.setText("Creating isolated instance…"); create.setDisable(true);
            String selectedVersion=version.getValue(), selectedLoader=loader.getValue(), selectedName=name.getText();
            java.nio.file.Path selectedImport=importPath[0];
            String selectedPackTitle=selectedPack==null?"":selectedPack.title();
            CompletableFuture.supplyAsync(()->{
                try{
                    java.nio.file.Path root=gameDirectory.isBlank()
                        ?java.nio.file.Path.of(System.getProperty("user.home"),".relian","instances")
                        :java.nio.file.Path.of(gameDirectory).resolve("RelianInstances");
                    var kind=chosen.startsWith("Vanilla")?com.relian.launcher.profiles.InstanceManager.Kind.VANILLA:
                        chosen.startsWith("Import")?com.relian.launcher.profiles.InstanceManager.Kind.IMPORT:
                        chosen.startsWith("Modpack")?com.relian.launcher.profiles.InstanceManager.Kind.MODPACK:
                        com.relian.launcher.profiles.InstanceManager.Kind.RELIAN;
                    return com.relian.launcher.profiles.InstanceManager.create(root,selectedName,selectedVersion,selectedLoader,
                        kind,selectedImport,selectedPackTitle);
                }catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((created,error)->javafx.application.Platform.runLater(()->{
                create.setDisable(false);
                if(error!=null){result.setText("Could not create instance: "+(error.getCause()==null?error.getMessage():error.getCause().getMessage()));return;}
                String message="Created "+created.manifest().name()+" at "+created.directory()+".";
                if(kindIncludesRelian(chosen)){
                    message+=" Relian module manifest and Minecraft-only FPS video defaults are included.";
                    if(selectedLoader.equals("Fabric")){
                        result.setText(message+" Installing compatible Modrinth performance modules…");
                        installRelianPerformanceMods(created.directory(),selectedVersion,result,message);
                    }else result.setText(message+" Fabric performance modules were not auto-installed because "+selectedLoader+" was selected; loader-specific module matching is still being added.");
                }else result.setText(message+" This instance has no Relian modules, as requested for Vanilla.");
            }));
        });
        panel.getChildren().addAll(nameLabel,name,typeLabel,type,versionRow,rootLabel,importBox,packBox,create,result);
        box.getChildren().addAll(panel,
            muted("Import copies game content such as mods, configs, resource packs, shaders, saves, and video options. It does not copy account tokens or launcher credentials."),
            muted("Modpack browsing currently creates a profile linked to the selected pack; importing the actual .mrpack contents and resolving its dependencies is the next implementation step."));
        return box;
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
                ". A Fabric loader installer and full game launch pipeline are still required before these mods can run."));
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

        VBox runtime=new VBox(4); runtime.setPadding(new Insets(16)); runtime.setStyle(cardStyle());
        Label javaTitle=new Label("Java runtime"); javaTitle.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");
        runtime.getChildren().addAll(javaTitle,muted("Relian's launcher uses Java 21. Per-version Minecraft Java runtime selection is not connected yet."));
        b.getChildren().addAll(appearance,performance,fpsTools,directories,runtime);
        return b;
    }

    private static String toHex(Color color){
        return String.format("#%02X%02X%02X",Math.round(color.getRed()*255),Math.round(color.getGreen()*255),Math.round(color.getBlue()*255));
    }
    private void info(String message){Alert a=new Alert(Alert.AlertType.INFORMATION);a.setTitle("Relian Client");a.setHeaderText("Not connected yet");a.setContentText(message);a.showAndWait();}
    public static void main(String[] args){launch(args);}
}
