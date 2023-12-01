package org.mslivo.core.engine.tools.save.settings.validator;

import org.mslivo.core.engine.tools.save.settings.SettingsManager;

import java.util.HashSet;
import java.util.function.Consumer;

public class StringValueValidator implements ValueValidator {

    private final HashSet<String> allowedValuesSet;

    private final int lengthMin, lengthMax;

    public StringValueValidator() {
        this(null, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public StringValueValidator(String[] allowedValues) {
        this(allowedValues, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public StringValueValidator(int lengthMin, int lengthMax) {
        this(null, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public StringValueValidator(String[] allowedValues, int lengthMin, int lengthMax) {
        this.lengthMin = lengthMin;
        this.lengthMax = lengthMax;
        this.allowedValuesSet = new HashSet<>();
        if(allowedValues != null){
            for(int i=0;i<allowedValues.length;i++){
                if(allowedValues[i] != null) allowedValuesSet.add(allowedValues[i]);
            }
        }
    }

    @Override
    public boolean isValueValid(String value) {
        if(!SettingsManager.isValidString(value)) return false;
        if(!this.allowedValuesSet.isEmpty() && !this.allowedValuesSet.contains(value)) return false;
        return value.length() >= lengthMin && value.length() <= lengthMax;
    }
}
