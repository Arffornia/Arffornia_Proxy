package fr.thegostsniper.arffornia_proxy.motd;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /motdpreview [YYYY-MM-DD] [en|fr]}: shows what the server list would display on that
 * day, and which entries were candidates. Permission {@code arffornia.motd.preview}.
 */
public final class MotdPreviewCommand implements SimpleCommand {

    public static final String PERMISSION = "arffornia.motd.preview";

    private final MotdService motd;

    public MotdPreviewCommand(MotdService motd) {
        this.motd = motd;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        LocalDate day = motd.today();
        String language = motd.languageFor(null);
        try {
            if (args.length >= 1) {
                day = LocalDate.parse(args[0]);
            }
        } catch (DateTimeParseException e) {
            invocation.source().sendMessage(Component.text("Usage: /motdpreview [YYYY-MM-DD] [en|fr]", NamedTextColor.RED));
            return;
        }
        if (args.length >= 2) {
            language = motd.languageFor(args[1]);
        }
        motd.reloadIfChanged();
        LocalDate shownDay = day;
        String shownLanguage = language;
        List<String> ids = motd.config()
                .map(c -> MotdSelector.candidates(c.entries(), shownDay).stream()
                        .map(MotdEntry::id).collect(Collectors.toList()))
                .orElse(List.of());
        invocation.source().sendMessage(Component.text(
                "MOTD on " + shownDay + " (" + shownLanguage + "), candidates: " + ids, NamedTextColor.GRAY));
        if (!motd.loaded()) {
            invocation.source().sendMessage(Component.text(
                    "No valid motd.yml loaded: showing the fallback. Fix it in Arffornia_Provisioning.", NamedTextColor.RED));
        }
        invocation.source().sendMessage(motd.render(shownDay, shownLanguage));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }
}
