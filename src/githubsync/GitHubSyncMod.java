package githubsync;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.scene.ui.TextField;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.dialogs.BaseDialog;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class GitHubSyncMod extends Mod{
    private static final long githubLimit = 100L * 1024L * 1024L;
    private static final DateTimeFormatter backupTime = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private Fi configFile;
    private Fi workDirectory;
    private SyncConfig config;
    private boolean busy;

    public GitHubSyncMod(){
        Events.on(ClientLoadEvent.class, event -> {
            workDirectory = Vars.dataDirectory.child("github-save-sync");
            configFile = workDirectory.child("config.json");
            config = SyncConfig.load(configFile);
            Vars.ui.menufrag.addButton("GitHub Save Sync", Icon.download, this::showMenu);
        });
    }

    private void showMenu(){
        BaseDialog dialog = new BaseDialog("GitHub Save Sync");
        dialog.cont.defaults().width(300f).height(55f).pad(4f);
        dialog.cont.button("Upload game data", Icon.upload, () -> {
            dialog.hide();
            upload(false);
        }).row();
        dialog.cont.button("Download game data", Icon.download, () -> {
            dialog.hide();
            download();
        }).row();
        dialog.cont.button("Connection settings", Icon.settings, () -> {
            dialog.hide();
            showSettings();
        }).row();
        dialog.addCloseButton();
        dialog.show();
    }

    private void showSettings(){
        BaseDialog dialog = new BaseDialog("GitHub Save Sync Settings");
        TextField owner = field(config.owner);
        TextField repository = field(config.repository);
        TextField branch = field(config.branch);
        TextField path = field(config.path);
        TextField token = field(config.token);
        token.setPasswordMode(true);
        token.setPasswordCharacter('•');

        dialog.cont.defaults().left().pad(5f);
        addField(dialog, "Repository owner", owner);
        addField(dialog, "Repository name", repository);
        addField(dialog, "Branch", branch);
        addField(dialog, "File path in repository", path);
        addField(dialog, "Fine-grained GitHub token", token);
        dialog.cont.add("Token permission: Contents — Read and write").colspan(2).wrap().width(420f).row();
        dialog.buttons.button("Cancel", dialog::hide).size(140f, 55f);
        dialog.buttons.button("Save", Icon.ok, () -> {
            config.owner = owner.getText().trim();
            config.repository = repository.getText().trim();
            config.branch = branch.getText().trim();
            config.path = path.getText().trim();
            config.token = token.getText().trim();
            config.lastSha = "";
            config.save(configFile);
            dialog.hide();
            Vars.ui.showInfo("GitHub Save Sync settings saved locally.");
        }).size(140f, 55f);
        dialog.show();
    }

    private TextField field(String value){
        TextField field = new TextField(value);
        field.setMaxLength(500);
        return field;
    }

    private void addField(BaseDialog dialog, String label, TextField field){
        dialog.cont.add(label).width(180f);
        dialog.cont.add(field).width(340f).height(50f).row();
    }

    private void upload(boolean overwriteConflict){
        if(!ready() || !start()) return;
        Vars.ui.showInfoToast("Checking GitHub…", 3f);
        GitHubClient client = new GitHubClient(config);
        client.metadata(remote -> ui(() -> {
            if(remote.exists && !overwriteConflict && !config.lastSha.equals(remote.sha)){
                finish();
                Vars.ui.showConfirm("Remote save changed", "The GitHub copy changed since this device last synchronized. Uploading would overwrite newer remote progress. Upload anyway?", () -> upload(true));
                return;
            }
            exportAndUpload(client, remote);
        }), this::failAsync);
    }

    private void exportAndUpload(GitHubClient client, GitHubClient.RemoteFile remote){
        Fi export = workDirectory.child("upload.zip");
        try{
            workDirectory.mkdirs();
            Vars.ui.settings.exportData(export);
            long length = export.length();
            if(length > githubLimit){
                export.delete();
                finish();
                Vars.ui.showErrorMessage("The export is " + formatSize(length) + ", above GitHub's 100 MiB Contents API limit. Remove large mods or cached assets and try again.");
                return;
            }
            byte[] bytes = export.readBytes();
            export.delete();
            Vars.ui.showInfoToast("Uploading " + formatSize(bytes.length) + "…", 4f);
            client.upload(bytes, remote, sha -> {
                Arrays.fill(bytes, (byte)0);
                ui(() -> {
                    config.lastSha = sha;
                    config.save(configFile);
                    finish();
                    Vars.ui.showInfo("Game data uploaded successfully.");
                });
            }, message -> {
                Arrays.fill(bytes, (byte)0);
                failAsync(message);
            });
        }catch(Exception error){
            Log.err(error);
            finish();
            Vars.ui.showException("Could not export game data", error);
        }
    }

    private void download(){
        if(!ready() || !start()) return;
        Vars.ui.showInfoToast("Checking GitHub…", 3f);
        GitHubClient client = new GitHubClient(config);
        client.metadata(remote -> {
            if(!remote.exists){
                failAsync("No synchronized save exists at " + config.path + ". Upload from the device with your current progress first.");
                return;
            }
            client.download(bytes -> ui(() -> confirmImport(bytes, remote.sha)), this::failAsync);
        }, this::failAsync);
    }

    private void confirmImport(byte[] bytes, String remoteSha){
        finish();
        Vars.ui.showConfirm("Replace local game data?", "This will first create a dated local backup, then replace local saves, campaign progress, settings, schematics, maps, and mods with the GitHub copy. Mindustry will close afterward. Continue?", () -> importDownloaded(bytes, remoteSha));
    }

    private void importDownloaded(byte[] bytes, String remoteSha){
        if(!start()) return;
        try{
            workDirectory.mkdirs();
            Fi backups = workDirectory.child("backups");
            backups.mkdirs();
            Fi backup = backups.child("before-download-" + backupTime.format(Instant.now()) + ".zip");
            Vars.ui.settings.exportData(backup);

            Fi downloaded = workDirectory.child("download.zip");
            downloaded.writeBytes(bytes, false);
            Arrays.fill(bytes, (byte)0);
            Vars.ui.settings.importData(downloaded);
            downloaded.delete();

            // The sync config lives outside Mindustry's exported folders, so it survives import.
            config.lastSha = remoteSha;
            config.save(configFile);
            finish();
            Vars.ui.showInfoOnHidden("Game data imported. A backup was saved as " + backup.name() + ". Mindustry will now close; reopen it to use the imported data.", Core.app::exit);
        }catch(Exception error){
            Arrays.fill(bytes, (byte)0);
            Log.err(error);
            finish();
            Vars.ui.showException("Could not import game data", error);
        }
    }

    private boolean ready(){
        if(config == null || !config.complete()){
            Vars.ui.showConfirm("Setup required", "Configure the GitHub repository and token first?", this::showSettings);
            return false;
        }
        return true;
    }

    private boolean start(){
        if(busy){
            Vars.ui.showInfoToast("A synchronization is already running.", 3f);
            return false;
        }
        busy = true;
        return true;
    }

    private void finish(){
        busy = false;
    }

    private void failAsync(String message){
        ui(() -> {
            finish();
            Vars.ui.showErrorMessage(message);
        });
    }

    private void ui(Runnable runnable){
        Core.app.post(runnable);
    }

    private String formatSize(long bytes){
        if(bytes >= 1024L * 1024L) return String.format("%.1f MiB", bytes / 1024d / 1024d);
        return String.format("%.1f KiB", bytes / 1024d);
    }
}
