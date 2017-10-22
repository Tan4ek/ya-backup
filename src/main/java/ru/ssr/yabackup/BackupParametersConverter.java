package ru.ssr.yabackup;

import com.beust.jcommander.IStringConverter;
import org.apache.commons.lang3.ArrayUtils;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BackupParametersConverter implements IStringConverter<List<BackupParameter>> {
    @Override
    public List<BackupParameter> convert(String value) {
        String[] backupParamStrings = value.split(";");
        backupParamStrings = ArrayUtils.isEmpty(backupParamStrings) ? new String[]{value} : backupParamStrings;
        return Arrays.stream(backupParamStrings).map(this::mapToBackupParameter).collect(Collectors.toList());
    }

    private BackupParameter mapToBackupParameter(String backupParamString) {
        String[] parameters = backupParamString.split(",");
        parameters = ArrayUtils.isEmpty(parameters) ? new String[]{backupParamString} : parameters;
        switch (parameters.length) {
            case 1:
                return new BackupParameter(Paths.get(parameters[0]));
            case 2:
            default:
                return new BackupParameter(Paths.get(parameters[0]), parameters[1]);
        }
    }
}
