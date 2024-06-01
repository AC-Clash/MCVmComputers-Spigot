package com.acclash.vmcomputers.utils;

import net.md_5.bungee.api.chat.*;

public class ChatUtil {

    public static BaseComponent createClickableMessage(net.md_5.bungee.api.ChatColor color, String message, String hoverMessage, String command) {

        TextComponent clickableMessage = new TextComponent(message);
        clickableMessage.setColor(color);
        clickableMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        clickableMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, (
                new ComponentBuilder(hoverMessage)).color(net.md_5.bungee.api.ChatColor.GRAY).italic(true).create()));

        return clickableMessage;
    }

    public static BaseComponent createClickableMessage(net.md_5.bungee.api.ChatColor color, String message, String hoverMessage, String command, boolean bold) {

        TextComponent clickableMessage = new TextComponent(message);
        clickableMessage.setColor(color);
        clickableMessage.setBold(Boolean.valueOf(bold));
        clickableMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        clickableMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, (
                new ComponentBuilder(hoverMessage)).color(net.md_5.bungee.api.ChatColor.GRAY).italic(true).create()));

        return clickableMessage;
    }

}
