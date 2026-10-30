import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** Camera-facing sprite drawing shared by the game and editor. */
final class ParticleSprites {
    static void draw(Graphics2D graphics,BufferedImage image,double[][] corners,float opacity) {
        Graphics2D g=(Graphics2D)graphics.create();
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0,Math.min(1,opacity))));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double[] a=corners[0],b=corners[1],d=corners[3];
        g.drawImage(image,new AffineTransform((b[0]-a[0])/image.getWidth(),(b[1]-a[1])/image.getWidth(),
                (d[0]-a[0])/image.getHeight(),(d[1]-a[1])/image.getHeight(),a[0],a[1]),null);
        g.dispose();
    }
}
