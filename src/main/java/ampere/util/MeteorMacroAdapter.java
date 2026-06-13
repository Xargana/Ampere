package ampere.util;

import java.util.List;

public class MeteorMacroAdapter {
    public static List<AmpereMacro> getMeteorMacros() {
        return AmpereCompatManager.getMeteorMacros();
    }

    public static void importToMeteor(AmpereMacro packUtilMacro) {
        if (AmpereCompatManager.importToMeteor(packUtilMacro)) {
            AmpereMessaging.sendPrefixed("§aImported to Meteor: " + packUtilMacro.name);
        } else {
            AmpereMessaging.sendPrefixed("§cFailed to import to Meteor");
        }
    }

    public static boolean importToAmpere(String macroName) {
        AmpereMacro packUtilMacro = AmpereCompatManager.getMeteorMacro(macroName);
        if (packUtilMacro == null) {
            AmpereMessaging.sendPrefixed("§cMeteor macro not found: " + macroName);
            return false;
        }

        AmpereMacro imported = AmpereMacroManager.get().addImportedCopy(packUtilMacro, packUtilMacro.name);
        if (imported == null) return false;

        if (!imported.name.equals(packUtilMacro.name)) {
            AmpereMessaging.sendPrefixed("§eImported Meteor macro as: " + imported.name);
        }
        return true;
    }
}
