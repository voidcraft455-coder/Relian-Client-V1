package com.relian.launcher;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Modality;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class RelianLauncher extends Application {
    private static final String BG="#17191e", PANEL="#20232a", ALT="#292e37", FG="#f2f4f7", MUTED="#a6acb7", BLUE="#4d83d8";
    private final BorderPane shell = new BorderPane();
    private final StackPane host = new StackPane();
    private ComboBox<String> versions;
    private String active = "Home";
    private String accountLabel = "Sign in with Microsoft";
    private final List<String> versionList = new ArrayList<>(List.of("26.3","26.2","1.21.11","1.21.10","1.20.6","1.20.1","1.19.4","1.18.2","1.8.9","1.8.8"));

    @Override public void start(Stage stage) {
        shell.setStyle("-fx-background-color:"+BG+";");
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
        for(String s:List.of("Home","Versions","Mods","Modpacks","Resource Packs","Shaders","Settings")) {
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
            case "Mods" -> content.getChildren().add(new com.relian.launcher.marketplace.MarketplaceBrowserPane(com.relian.launcher.marketplace.ProjectType.MOD));
            case "Modpacks" -> content.getChildren().add(new com.relian.launcher.marketplace.MarketplaceBrowserPane(com.relian.launcher.marketplace.ProjectType.MODPACK));
            case "Resource Packs" -> content.getChildren().add(new com.relian.launcher.marketplace.MarketplaceBrowserPane(com.relian.launcher.marketplace.ProjectType.RESOURCE_PACK));
            case "Shaders" -> content.getChildren().add(new com.relian.launcher.marketplace.MarketplaceBrowserPane(com.relian.launcher.marketplace.ProjectType.SHADER));
            default -> content.getChildren().add(settings());
        }
        ScrollPane scroll=new ScrollPane(content); scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background:"+BG+";-fx-background-color:"+BG+";-fx-border-color:transparent;");
        host.getChildren().add(scroll);
        if(shell.getLeft() instanceof VBox side) for(var node:side.getChildren()) if(node instanceof Button b) b.setStyle(navStyle(b.getText().equals(name)));
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
    private VBox settings(){
        VBox b=new VBox(12);for(String[] item:List.of(new String[]{"Game directory","Separate folders for each installation profile."},new String[]{"Memory allocation","Choose the RAM limit for Minecraft."},new String[]{"Java runtime","Select or manage a compatible Java runtime."},new String[]{"Launcher updates","Check for Relian launcher updates."})){
            VBox row=new VBox(4);row.setPadding(new Insets(16));row.setStyle(cardStyle());Label t=new Label(item[0]);t.setStyle("-fx-text-fill:"+FG+";-fx-font-weight:bold;");row.getChildren().addAll(t,muted(item[1]));b.getChildren().add(row);}
        return b;
    }
    private void info(String message){Alert a=new Alert(Alert.AlertType.INFORMATION);a.setTitle("Relian Client");a.setHeaderText("Not connected yet");a.setContentText(message);a.showAndWait();}
    public static void main(String[] args){launch(args);}
}
