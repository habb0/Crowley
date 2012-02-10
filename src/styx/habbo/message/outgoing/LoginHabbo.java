package styx.habbo.message.outgoing;

import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;
import styx.habbo.beans.Ban;
import styx.habbo.beans.Fuseright;
import styx.habbo.beans.Habbo;
import styx.habbo.game.GameSession;
import styx.habbo.message.ServerMessage;
import styx.util.DatastoreUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Set;

/**
 * "THE BEER-WARE LICENSE" (Revision 42):
 * <crowlie@hybridcore.net> wrote this file. As long as you retain this notice you
 * can do whatever you want with this stuff. If we meet some day, and you think
 * this stuff is worth it, you can buy me a beer in return Crowley.
 */
public class LoginHabbo implements Runnable {
    private static Logger logger = Logger.getLogger(LoginHabbo.class.getName());

    private GameSession networkGameSession;
    private String ssoTicket;

    public LoginHabbo(GameSession networkGameSession, String ssoTicket) {
        this.networkGameSession = networkGameSession;
        this.ssoTicket = ssoTicket;
    }

    public void run() {
        Session session = DatastoreUtil.currentSession();

        Habbo habbo = (Habbo)session.createCriteria(Habbo.class).add(Restrictions.eq("ssoTicket", this.ssoTicket)).uniqueResult();

        // Invalid login ticket :o
        if (habbo == null) {
            this.networkGameSession.sendAlert("Invalid login ticket, refresh the client and try again.");
            this.networkGameSession.getChannel().close();
            return;
        }

        Date now = GregorianCalendar.getInstance().getTime();
        if (! habbo.getSsoIp().equals(this.networkGameSession.getIP()) || now.after(habbo.getSsoExpires())) {
            this.networkGameSession.sendAlert("Invalid login ticket, refresh the client and try again.");
            this.networkGameSession.getChannel().disconnect();
            return;
        }

        for (Ban ban : habbo.getBans()) {
            if (ban.getExpires().after(now)) {
                this.networkGameSession.sendMessage(
                        new ServerMessage(35)
                        .appendString("You have been banned from the hotel: ", 13)
                        .appendString(ban.getReason(), 13)
                        .appendString("This ban will expire on " + (new SimpleDateFormat("dd-MM-yyyy")).format(ban.getExpires()))
                );

                this.networkGameSession.getChannel().disconnect();
                return;
            }
        }

        Set<Fuseright> rights = habbo.getFuserank().getRights();

        ServerMessage serverMessage = new ServerMessage(2);
        serverMessage.append(rights.size());
        
        boolean isMod = false;
        for (Fuseright right : rights) {
            if (right.getRight().equals("fuse_mod")) {
                isMod = true;
            }

            serverMessage.appendString(right.getRight());
        }

        this.networkGameSession.sendMessage(serverMessage);

        if (isMod) {
            //TODO: Show mod tools
        }

        //TODO: Send effects inventory

        this.networkGameSession.sendMessage(
                new ServerMessage(290)
                .append(true)
                .append(false)
        );

        this.networkGameSession.sendMessage(
                new ServerMessage(3)
        );

        this.networkGameSession.sendMessage(
                new ServerMessage(517)
                .append(true)
        );

        //TODO: Update pixels
        //TODO: Home room
        //TODO: Favourite rooms

        this.networkGameSession.getMessageHandler().unregisterLoginHandlers();
        //TODO: Register other handlers
    }
}