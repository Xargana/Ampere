package ampere.mixin;

import ampere.commands.AmpereCommandSource;
import ampere.commands.AmpereCommands;
import ampere.mixin.accessor.AmpereCommandSuggestionsListAccessor;
import ampere.modules.PackHideState;
import ampere.util.AmpereCompatManager;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class AmpereChatSuggestMixin {
    @Shadow @Final private EditBox input;
    @Shadow @Nullable private ParseResults<ClientSuggestionProvider> currentParse;
    @Shadow @Nullable private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow @Nullable private CommandSuggestions.SuggestionsList suggestions;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;
    @Shadow private boolean currentParseIsCommand;
    @Shadow private boolean currentParseIsMessage;
    @Shadow private boolean keepSuggestions;

    @Unique private boolean Ampere$owningSuggestions;

    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);
    @Shadow private void updateUsageInfo(ParseResults<ClientSuggestionProvider> parseResults, Suggestions suggestions) {}

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void Ampere$updateAmpereCommandInfo(CallbackInfo ci) {
        String value = input.getValue();
        if (Ampere$shouldSuppressMeteorSuggestions(value)) {
            Ampere$clearActiveSuggestions();
            ci.cancel();
            return;
        }

        if (!Ampere$isAmpereCommandInput(value)) {
            if (Ampere$owningSuggestions) Ampere$clearActiveSuggestions();
            return;
        }

        int prefixLen = AmpereCommands.effectivePrefix().length();
        StringReader reader = new StringReader(value);
        reader.setCursor(prefixLen);
        int cursor = Math.max(prefixLen, Math.min(input.getCursorPosition(), value.length()));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ParseResults<AmpereCommandSource> parse = AmpereCommands.dispatcher().parse(reader, AmpereCommandSource.INSTANCE);

        @SuppressWarnings("unchecked")
        ParseResults<ClientSuggestionProvider> widgetParse =
                (ParseResults<ClientSuggestionProvider>) (Object) parse;

        currentParse = widgetParse;
        currentParseIsCommand = true;
        currentParseIsMessage = false;
        commandUsage.clear();

        if (suggestions != null && keepSuggestions && Ampere$canKeepSuggestionList(value)) {
            ci.cancel();
            return;
        }

        Ampere$clearActiveSuggestions();

        String inputSnapshot = value;
        CompletableFuture<Suggestions> future = AmpereCommands.dispatcher()
                .getCompletionSuggestions(parse, cursor);
        pendingSuggestions = future;
        future.thenAccept(result -> {
            if (pendingSuggestions != future) return;
            if (!inputSnapshot.equals(input.getValue())) return;
            if (!Ampere$isAmpereCommandInput(inputSnapshot)) return;
            Ampere$owningSuggestions = true;
            updateUsageInfo(widgetParse, result);
            if (pendingSuggestions == future && inputSnapshot.equals(input.getValue())) {
                showSuggestions(false);
            }
        });
        ci.cancel();
    }

    @Inject(method = "updateCommandInfo", at = @At("TAIL"))
    private void Ampere$hideLateMeteorSuggestionsInPanic(CallbackInfo ci) {
        if (Ampere$shouldSuppressMeteorSuggestions(input.getValue())) {
            Ampere$clearActiveSuggestions();
        }
    }

    @Inject(method = "showSuggestions", at = @At("HEAD"), cancellable = true)
    private void Ampere$hideShownMeteorSuggestionsInPanic(boolean narrateFirstSuggestion, CallbackInfo ci) {
        if (Ampere$shouldSuppressMeteorSuggestions(input.getValue())) {
            Ampere$clearActiveSuggestions();
            ci.cancel();
        }
    }

    @Unique
    private static boolean Ampere$isAmpereCommandInput(String value) {
        return !AmpereCommands.commandsBlockedByPanic() && AmpereCommands.isAmpereCommandMessage(value);
    }

    @Unique
    private static boolean Ampere$shouldSuppressMeteorSuggestions(String value) {
        if (!PackHideState.isActive()) return false;
        if (!AmpereCompatManager.isMeteorAvailable()) return false;
        if (value == null) return false;

        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("/")) return false;

        String prefix = Ampere$meteorPrefix();
        return !prefix.isEmpty() && trimmed.startsWith(prefix);
    }

    @Unique
    private static String Ampere$meteorPrefix() {
        try {
            Class<?> configClass = Class.forName("meteordevelopment.meteorclient.systems.config.Config");
            Object config = configClass.getMethod("get").invoke(null);
            Object prefixSetting = configClass.getField("prefix").get(config);
            Object prefix = prefixSetting.getClass().getMethod("get").invoke(prefixSetting);
            if (prefix instanceof String stringPrefix && !stringPrefix.isBlank()) {
                return stringPrefix.trim();
            }
        } catch (Throwable ignored) {
        }
        return ".";
    }

    @Unique
    private boolean Ampere$canKeepSuggestionList(String currentInput) {
        if (suggestions == null || currentInput == null) return false;
        try {
            AmpereCommandSuggestionsListAccessor accessor =
                    (AmpereCommandSuggestionsListAccessor) suggestions;
            String original = accessor.Ampere$getOriginalContents();
            List<Suggestion> list = accessor.Ampere$getSuggestionList();
            if (original == null || list == null || list.isEmpty()) return false;
            for (Suggestion suggestion : list) {
                if (suggestion == null) continue;
                try {
                    if (currentInput.equals(suggestion.apply(original))) return true;
                } catch (Throwable ignored) {
                    return false;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    @Unique
    private void Ampere$clearActiveSuggestions() {
        input.setSuggestion(null);
        suggestions = null;
        pendingSuggestions = null;
        keepSuggestions = false;
        Ampere$owningSuggestions = false;
    }
}
