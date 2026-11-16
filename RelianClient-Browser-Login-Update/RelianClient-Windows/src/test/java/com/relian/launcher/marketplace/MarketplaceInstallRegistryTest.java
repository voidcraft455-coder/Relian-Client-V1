package com.relian.launcher.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MarketplaceInstallRegistryTest {
    @TempDir Path temp;

    @Test void recordsAndListsProviderVersionAndHashMetadata() throws Exception {
        Path mods=Files.createDirectory(temp.resolve("mods"));
        Path jar=Files.writeString(mods.resolve("sample.jar"),"jar bytes");
        MarketplaceInstallRegistry.record(temp,jar,"Modrinth","project-1","version-2","sha512","abc123",
                ProjectType.MOD,"1.21.1","fabric");
        var records=MarketplaceInstallRegistry.list(temp);
        assertEquals(1,records.size());
        assertEquals("project-1",records.get(0).projectId());
        assertEquals("version-2",records.get(0).versionId());
        assertEquals("mods/sample.jar",records.get(0).relativePath().replace('\\','/'));
        assertEquals("sha512",records.get(0).hashAlgorithm());
    }

    @Test void updatesExistingPathRecordInsteadOfDuplicating() throws Exception {
        Path mods=Files.createDirectory(temp.resolve("mods"));
        Path jar=Files.writeString(mods.resolve("sample.jar"),"bytes");
        MarketplaceInstallRegistry.record(temp,jar,"Modrinth","project","v1","sha512","one",ProjectType.MOD,"1.21.1","fabric");
        MarketplaceInstallRegistry.record(temp,jar,"Modrinth","project","v2","sha512","two",ProjectType.MOD,"1.21.1","fabric");
        assertEquals(1,MarketplaceInstallRegistry.list(temp).size());
        assertEquals("v2",MarketplaceInstallRegistry.list(temp).get(0).versionId());
    }

    @Test void resolvesTrackedFileAfterEnableDisableRenameAndCanForgetOriginalRecord() throws Exception {
        Path mods=Files.createDirectory(temp.resolve("mods"));
        Path jar=Files.writeString(mods.resolve("toggle.jar"),"bytes");
        MarketplaceInstallRegistry.record(temp,jar,"Modrinth","toggle-project","v1","sha512","hash",ProjectType.MOD,"1.21.1","fabric");
        Path disabled=Files.move(jar,mods.resolve("toggle.jar.disabled"));
        var record=MarketplaceInstallRegistry.list(temp).get(0);
        assertEquals(disabled.toRealPath(),MarketplaceInstallRegistry.resolveFile(temp,record).toRealPath());
        MarketplaceInstallRegistry.forgetRecord(temp,record);
        assertTrue(MarketplaceInstallRegistry.list(temp).isEmpty());
    }

    @Test void rejectsFilesOutsideManagedFolders() throws Exception {
        Path outside=Files.writeString(temp.resolve("unmanaged.jar"),"bytes");
        assertThrows(java.io.IOException.class,()->MarketplaceInstallRegistry.record(temp,outside,"Modrinth","p","v","sha512","h",ProjectType.MOD,"1.21.1","fabric"));
    }
}
