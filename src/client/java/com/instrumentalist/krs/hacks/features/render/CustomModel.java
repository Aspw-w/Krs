package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.render.custommodel.CustomModelRenderer;
import com.instrumentalist.krs.utils.render.custommodel.ModelCatalog;
import com.instrumentalist.krs.utils.render.custommodel.ModelProfile;
import com.instrumentalist.krs.utils.value.ListValue;

public final class CustomModel extends Module {
    private static final String NONE = "None";

    @Setting
    public static final ListValue selfModel = new ListValue("Self Model", options(), ModelCatalog.MODELS.get(0).label);

    @Setting
    public static final ListValue othersModel = new ListValue("Others Model", options(), NONE);

    public CustomModel() {
        super("Custom Model", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, true);
    }

    private static String[] options() {
        String[] labels = ModelCatalog.labels();
        String[] options = new String[labels.length + 1];
        options[0] = NONE;
        System.arraycopy(labels, 0, options, 1, labels.length);
        return options;
    }

    public static ModelProfile profileFor(boolean self) {
        int index = (self ? selfModel : othersModel).getCurrentIndex() - 1;
        return index < 0 || index >= ModelCatalog.MODELS.size() ? null : ModelCatalog.MODELS.get(index);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        CustomModelRenderer.reset();
    }
}
