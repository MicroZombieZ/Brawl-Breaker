import java.awt.*;
import java.awt.image.*;
import java.lang.reflect.*;
import java.util.*;
import javax.swing.*;

/** Exercise the game's actual projection path and shared opaque/transparent rasterizer. */
public class WorldRenderCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static SceneDepthRenderer.Face project(CubeMovement game,double[][] vertices,Color color,BufferedImage image)throws Exception{
        Class<?> type=Class.forName("CubeMovement$RenderFace");Constructor<?> constructor=type.getDeclaredConstructor(double[][].class,Color.class);constructor.setAccessible(true);Object face=constructor.newInstance(vertices,color);
        Field texture=type.getDeclaredField("image");texture.setAccessible(true);texture.set(face,image);Method method=CubeMovement.class.getDeclaredMethod("projectFace",type);method.setAccessible(true);return (SceneDepthRenderer.Face)method.invoke(game,face);
    }
    static void run()throws Exception{
        CubeMovement game=new CubeMovement();game.setSize(800,600);MovementCheck.field("actualCameraDistance").setDouble(game,10);MovementCheck.field("actualCameraPitch").setDouble(game,0);MovementCheck.field("cameraTargetY").setDouble(game,0);
        SceneDepthRenderer.Face red=project(game,new double[][]{{-.4,-.4,8},{2,-2,0},{2,2,0},{-.4,.4,8}},Color.RED,null),blue=project(game,new double[][]{{-1,-1,5},{1,-1,5},{1,1,5},{-1,1,5}},Color.BLUE,null);
        SceneDepthRenderer renderer=new SceneDepthRenderer();renderer.render(800,600,Arrays.asList(red,blue),Collections.emptySet());check((renderer.image.getRGB(400,330)&0xffffff)==0xff0000,"Game slanted surface wins by actual pixel depth");int[] original=renderer.pixels.clone();renderer.render(800,600,Arrays.asList(blue,red),Collections.emptySet());check(Arrays.equals(original,renderer.pixels),"Game face ordering does not change visibility");
        BufferedImage texture=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<2;y++)for(int x=0;x<2;x++)texture.setRGB(x,y,0x80ff0000);
        double[][] front={{-1,1,6},{1,1,6},{1,-1,6},{-1,-1,6}},behind={{-1,1,4},{1,1,4},{1,-1,4},{-1,-1,4}};
        SceneDepthRenderer.Face particle=project(game,front,Color.WHITE,texture),hidden=project(game,behind,Color.WHITE,texture);
        renderer.render(800,600,Arrays.asList(blue,hidden),Collections.emptySet());check((renderer.image.getRGB(400,330)&0xffffff)==0xff,"Wall occludes image particle");
        renderer.render(800,600,Arrays.asList(particle,blue),Collections.emptySet());int mixed=renderer.image.getRGB(400,330);check((mixed>>16&255)>=127&&(mixed&255)>=126,"Visible texture alpha blends with wall");
        SceneDepthRenderer.Face green=project(game,new double[][]{{-1,1,7},{1,1,7},{1,-1,7},{-1,-1,7}},new Color(0,255,0,128),null);
        renderer.render(800,600,Arrays.asList(particle,blue,green),Collections.emptySet());int first=renderer.image.getRGB(400,330);renderer.render(800,600,Arrays.asList(green,particle,blue),Collections.emptySet());check(renderer.image.getRGB(400,330)==first,"Transparency composites by per-pixel depth, independent of submission order");
        SceneDepthRenderer.Face crossing=project(game,new double[][]{{-.1,-.2,9},{1,-.2,11},{1,.2,11},{-.1,.2,9}},Color.WHITE,texture);check(crossing!=null,"Image particle clips instead of disappearing at camera");renderer.render(800,600,Arrays.asList(crossing),Collections.emptySet());check(Arrays.stream(renderer.pixels).anyMatch(c->(c>>>24)>0),"Clipped texture remains visible");
        texture.setRGB(0,0,0);SceneDepthRenderer.Face hole=project(game,front,Color.WHITE,texture);renderer.render(800,600,Arrays.asList(hole,blue),Collections.emptySet());check((renderer.image.getRGB(350,280)&0xffffff)==0xff,"Transparent image texels preserve background");
    }
    public static void main(String[] args){try{SwingUtilities.invokeAndWait(()->{try{run();}catch(Exception e){throw new RuntimeException(e);}});System.out.println("Passed: game projection, depth ordering, hidden particles, per-pixel transparency, clipped textures and alpha holes.");System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
