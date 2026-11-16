import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import javax.swing.SwingUtilities;

public class CameraCheck {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                MapDefinition.Part mesh=new MapDefinition.Part();mesh.shape=MapDefinition.Shape.SPHERE;mesh.w=mesh.h=mesh.d=2;
                if(CameraSurfaces.hit(new double[]{.9,.1,-3},new double[]{0,0,6},mesh,.05)!=1)throw new AssertionError("Sphere bounding-box corner still blocks camera");
                mesh.shape=MapDefinition.Shape.WEDGE;
                if(CameraSurfaces.hit(new double[]{-3,1.8,.8},new double[]{6,0,0},mesh,.05)!=1)throw new AssertionError("Empty space above wedge blocks camera");
                mesh.shape=MapDefinition.Shape.BOX;mesh.w=.2;mesh.d=4;mesh.yaw=45;
                if(CameraSurfaces.hit(new double[]{1.3,1,-3},new double[]{0,0,2},mesh,.05)!=1)throw new AssertionError("Rotated bounding-box corner blocks camera");
                double[] a={0,0,0},b={2,0,0},c={0,2,0};
                for(int sign:new int[]{-1,1}){double t=CameraSurfaces.triangle(new double[]{.5,.5,2*sign},new double[]{0,0,-4*sign},a,b,c,.25);if(Math.abs(t-.4375)>1e-9)throw new AssertionError("Both rendered face sides collide");}
                double edgeHit=CameraSurfaces.triangle(new double[]{1,-.1,-2},new double[]{0,0,4},a,b,c,.25);
                if(Math.abs(edgeHit-(2-Math.sqrt(.25*.25-.1*.1))/4)>1e-9)throw new AssertionError("Swept camera edge clearance");
                double vertexHit=CameraSurfaces.triangle(new double[]{-.1,-.1,-2},new double[]{0,0,4},a,b,c,.25);
                if(Math.abs(vertexHit-(2-Math.sqrt(.25*.25-.02))/4)>1e-9)throw new AssertionError("Swept camera vertex clearance");
                CubeMovement demo = new CubeMovement();
                demo.setSize(1000, 700);
                Method camera = CubeMovement.class.getDeclaredMethod("cameraPoint", double.class, double.class, double.class);
                camera.setAccessible(true);
                Method resolve = CubeMovement.class.getDeclaredMethod("resolveCamera");
                resolve.setAccessible(true);
                Field distance = CubeMovement.class.getDeclaredField("actualCameraDistance");
                Field actualPitch = CubeMovement.class.getDeclaredField("actualCameraPitch");
                distance.setAccessible(true);
                actualPitch.setAccessible(true);
                Field x = CubeMovement.class.getDeclaredField("x"), z = CubeMovement.class.getDeclaredField("z");
                Field yaw = CubeMovement.class.getDeclaredField("yaw"), pitch = CubeMovement.class.getDeclaredField("pitch");
                for (Field field : new Field[]{x,z,yaw,pitch}) field.setAccessible(true);
                Method move = CubeMovement.class.getDeclaredMethod("move", double.class);
                move.setAccessible(true);
                demo.getActionMap().get("87:false").actionPerformed(null);
                move.invoke(demo, 1.0);
                if (x.getDouble(demo) != 0 || z.getDouble(demo) != 0)
                    throw new AssertionError("Movement continued while menu was open");
                for (double position : new double[]{-9.5,0,9.5}) {
                    x.setDouble(demo, position);
                    z.setDouble(demo, position);
                    for (double angle : new double[]{0, Math.PI/2, Math.PI, -Math.PI/2}) {
                        yaw.setDouble(demo, angle);
                        for (double elevation : new double[]{-89,-45,0,15,35,85,89}) {
                            pitch.setDouble(demo, Math.toRadians(elevation));
                            resolve.invoke(demo);
                            double[] center = (double[]) camera.invoke(demo, position, 0.5, position);
                            double actualDistance = distance.getDouble(demo);
                            double actualAngle = actualPitch.getDouble(demo);
                            if (Math.abs(center[0]) > 1e-9 || Math.abs(center[1]) > 1e-9 || Math.abs(center[2]-actualDistance) > 1e-9)
                                throw new AssertionError("Camera does not track cube center");
                            if (.5 + actualDistance * Math.sin(actualAngle) < .25 - 1e-9)
                                throw new AssertionError("Camera penetrates ground");
                            double horizontal = actualDistance * Math.cos(actualAngle);
                            if (Math.abs(horizontal * Math.sin(angle)) < .75 &&
                                    Math.abs(horizontal * Math.cos(angle)) < .75 &&
                                    Math.abs(actualDistance * Math.sin(actualAngle)) < .75)
                                throw new AssertionError("Camera enters player cube");
                            BufferedImage frame = new BufferedImage(1000,700,BufferedImage.TYPE_INT_RGB);
                            java.awt.Graphics2D graphics = frame.createGraphics();
                            demo.paint(graphics);
                            graphics.dispose();
                        }
                    }
                }
                x.setDouble(demo, 0);
                z.setDouble(demo, 0);
                yaw.setDouble(demo, 0);
                pitch.setDouble(demo, 0);
                Field obstacles = CubeMovement.class.getDeclaredField("cameraObstacles");
                obstacles.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<double[]> boxes = (java.util.List<double[]>) obstacles.get(demo);
                boxes.add(new double[]{-2,0,3,2,3,3.01});
                resolve.invoke(demo);
                if (distance.getDouble(demo) > 2.75 || distance.getDouble(demo) < 2.7)
                    throw new AssertionError("Camera passed through thin obstacle");
                boxes.clear();
                resolve.invoke(demo);
                if (Math.abs(distance.getDouble(demo)-8)>1e-9)
                    throw new AssertionError("Camera did not restore distance");
                Field active=CubeMovement.class.getDeclaredField("activeMap");active.setAccessible(true);MapDefinition map=(MapDefinition)active.get(demo);
                MapDefinition.Part sphere=new MapDefinition.Part();sphere.shape=MapDefinition.Shape.SPHERE;sphere.w=sphere.h=sphere.d=2;sphere.x=1.2;sphere.z=3;sphere.collision=false;map.objects.add(sphere);
                resolve.invoke(demo);if(Math.abs(distance.getDouble(demo)-8)>1e-9)throw new AssertionError("Camera should clear empty sphere bounding-box space");
                sphere.x=0;resolve.invoke(demo);if(distance.getDouble(demo)>=3)throw new AssertionError("Visible surface blocks camera independently of player collision flag");
                map.objects.clear();resolve.invoke(demo);if(Math.abs(distance.getDouble(demo)-8)>1e-9)throw new AssertionError("Removing mesh restores camera distance");
                System.out.println("Passed: ground/player collision, thin-object collision, clear-view recovery, full orbit rendering, pause.");
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        System.exit(0);
    }
}
