import java.util.*;
/** Shared polygon meshes for the game and map editor. */
final class MapMeshes {
    static java.util.List<double[][]> faces(MapDefinition.Part p){
        List<double[][]> faces=new ArrayList<>();
        if(p.shape==MapDefinition.Shape.BOX||p.shape==MapDefinition.Shape.WEDGE){
            double[][] v={{-.5,0,-.5},{.5,0,-.5},{.5,0,.5},{-.5,0,.5},{-.5,1,-.5},{.5,1,-.5},{.5,1,.5},{-.5,1,.5}};
            if(p.shape==MapDefinition.Shape.WEDGE){v[6][1]=0;v[7][1]=0;}
            for(int[] f:new int[][]{{0,1,2,3},{4,7,6,5},{0,4,5,1},{1,5,6,2},{2,6,7,3},{3,7,4,0}}){double[][] a=new double[4][];for(int i=0;i<4;i++)a[i]=v[f[i]].clone();faces.add(a);}
        }else{
            int rings=p.shape==MapDefinition.Shape.SPHERE?8:1;
            for(int r=0;r<rings;r++)for(int i=0;i<16;i++){
                double[][] f=new double[4][3];int[] rr={r,r,r+1,r+1},ii={i,i+1,i+1,i};
                for(int j=0;j<4;j++){double t=(double)rr[j]/rings,a=ii[j]*Math.PI/8,rad=p.shape==MapDefinition.Shape.SPHERE?Math.sin(t*Math.PI)*.5:.5;f[j]=new double[]{Math.cos(a)*rad,p.shape==MapDefinition.Shape.SPHERE?(1-Math.cos(t*Math.PI))/2:t,Math.sin(a)*rad};}faces.add(f);
            }
            if(p.shape==MapDefinition.Shape.CYLINDER)for(int top=0;top<2;top++){double[][] f=new double[16][3];for(int i=0;i<16;i++)f[i]=new double[]{Math.cos(i*Math.PI/8)*.5,top,Math.sin(i*Math.PI/8)*.5};faces.add(f);}
        }
        double a=Math.toRadians(p.yaw),c=Math.cos(a),s=Math.sin(a);
        for(double[][] face:faces)for(double[] v:face){double x=v[0]*p.w,z=v[2]*p.d;v[0]=p.x+c*x+s*z;v[1]=p.y+v[1]*p.h;v[2]=p.z-s*x+c*z;}return faces;
    }
    static double[][] plane(MapDefinition m){if(m.plane==MapDefinition.Plane.RECTANGLE)return new double[][]{{-m.width/2,0,-m.depth/2},{m.width/2,0,-m.depth/2},{m.width/2,0,m.depth/2},{-m.width/2,0,m.depth/2}};double[][] v=new double[64][3];for(int i=0;i<v.length;i++)v[i]=new double[]{Math.cos(i*Math.PI/32)*m.width/2,0,Math.sin(i*Math.PI/32)*m.depth/2};return v;}
}
