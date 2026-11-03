import java.awt.*;
import javax.swing.*;

/** Entry points for local practice and LAN play. */
final class OpeningMenu extends JPanel {
    final JButton host=new JButton("Host"),join=new JButton("Join"),quickMatch=new JButton("Quick Match"),practice=new JButton("Practice");
    final JButton quit=new JButton("Quit");
    OpeningMenu(Runnable onHost,Runnable onJoin,Runnable onPractice,Runnable onQuit){
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        setBackground(new Color(24,34,49));
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(72,108,137)),BorderFactory.createEmptyBorder(32,48,32,48)));
        JLabel title=new JLabel("CUBE MOVEMENT");title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,32));title.setForeground(Color.WHITE);
        JLabel subtitle=new JLabel("Play together. Train your moves.");subtitle.setForeground(new Color(174,195,215));
        for(JComponent c:new JComponent[]{title,subtitle,host,join,quickMatch,practice,quit}){
            c.setAlignmentX(Component.CENTER_ALIGNMENT);add(c);add(Box.createVerticalStrut(16));
        }
        for(JButton b:new JButton[]{host,join,quickMatch,practice,quit}){b.setMaximumSize(new Dimension(310,46));b.setPreferredSize(new Dimension(310,46));b.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));}
        quit.addActionListener(e->onQuit.run());
        quickMatch.setEnabled(false);quickMatch.setToolTipText("Quick Match is not available yet.");
        host.addActionListener(e->onHost.run());join.addActionListener(e->onJoin.run());practice.addActionListener(e->onPractice.run());
        setVisible(false);
    }
}
