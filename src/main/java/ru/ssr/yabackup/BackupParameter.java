package ru.ssr.yabackup;

import java.nio.file.Path;

public class BackupParameter {

    private Path backupFile;

    private String archivName;

    public BackupParameter(Path backupFile) {
        this.backupFile = backupFile;
        this.archivName = backupFile.getFileName().toString();
    }

    public BackupParameter(Path backupFile, String archivName) {
        this.backupFile = backupFile;
        this.archivName = archivName;
    }

    public Path getBackupFile() {
        return backupFile;
    }

    public String getArchivName() {
        return archivName;
    }
}
