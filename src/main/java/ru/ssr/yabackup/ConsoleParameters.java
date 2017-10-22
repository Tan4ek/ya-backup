package ru.ssr.yabackup;

import com.beust.jcommander.Parameter;

import java.nio.file.Path;
import java.util.List;

public class ConsoleParameters {
    @Parameter(names = {"--token"}, description = "Yandex disk o-token", required = true)
    private String token;

    @Parameter(names = {"--user"}, description = "Yandex username", required = true)
    private String user;

    @Parameter(names = {"--output"}, description = "Yandex disk folder where will upload backup", required = true)
    private String yandexDiskFolder;

    @Parameter(names = {"--backup"}, description = "Local folders or files for backuping", required = true, listConverter = BackupParametersConverter.class, validateValueWith = BackupParametersValidation.class)
    private List<BackupParameter> backuping;

    @Parameter(names = {"--remove_archive_after_backup"}, description = "Remove archive after send to yandex disk")
    private boolean removeArchiveAfterBackup = true;

    @Parameter(names = {"--archives_output"}, description = "Place where temporary storage zip archives")
    private Path archivesOutput;


    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getYandexDiskFolder() {
        return yandexDiskFolder;
    }

    public void setYandexDiskFolder(String yandexDiskFolder) {
        this.yandexDiskFolder = yandexDiskFolder;
    }

    public List<BackupParameter> getBackuping() {
        return backuping;
    }

    public void setBackuping(List<BackupParameter> backuping) {
        this.backuping = backuping;
    }

    public boolean isRemoveArchiveAfterBackup() {
        return removeArchiveAfterBackup;
    }

    public void setRemoveArchiveAfterBackup(boolean removeArchiveAfterBackup) {
        this.removeArchiveAfterBackup = removeArchiveAfterBackup;
    }

    public Path getArchivesOutput() {
        return archivesOutput;
    }

    public void setArchivesOutput(Path archivesOutput) {
        this.archivesOutput = archivesOutput;
    }
}
