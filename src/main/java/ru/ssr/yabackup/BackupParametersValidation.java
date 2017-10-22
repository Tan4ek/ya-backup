package ru.ssr.yabackup;

import com.beust.jcommander.IValueValidator;
import com.beust.jcommander.ParameterException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class BackupParametersValidation implements IValueValidator<List<BackupParameter>> {
    @Override
    public void validate(String name, List<BackupParameter> value) throws ParameterException {
        if (Objects.isNull(value) || value.isEmpty()) {
            throw new ParameterException(name + " is not exist");
        }
        Set<String> archiveNames = new HashSet<>();
        for (BackupParameter backupParameter : value) {
            Path backupFile = backupParameter.getBackupFile();
            if (Files.notExists(backupFile)) {
                throw new ParameterException(name + " path " + backupFile.toString() + " not exist");
            }
            if (archiveNames.contains(backupParameter.getArchivName())) {
                throw new ParameterException(name + " duplicate archive names " + backupParameter.getArchivName());
            } else {
                archiveNames.add(backupParameter.getArchivName());
            }
        }
    }
}
