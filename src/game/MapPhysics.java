/** Shared solo/host map collision and lightweight loose-object physics. */
final class MapPhysics {
    static double[] boundary(MapDefinition map,double x,double z,double radius){
        double a=Math.max(.1,map.width/2-radius),b=Math.max(.1,map.depth/2-radius);
        x=Math.max(-a,Math.min(a,x));z=Math.max(-b,Math.min(b,z));
        if(map.plane==MapDefinition.Plane.ELLIPSE){double distance=Math.hypot(x/a,z/b);if(distance>1){x/=distance;z/=distance;}}
        return new double[]{x,z};
    }
    private static final double EPS=1e-8;
    private static final java.util.Map<MapDefinition.Part,Hull> HULLS=new java.util.WeakHashMap<>();
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    /** SAT axes include every face normal and every mesh-edge / actor-edge cross product. */
    private static final class Hull {
        final double w,h,d,yaw;final MapDefinition.Shape shape;
        final java.util.List<double[]> axes=new java.util.ArrayList<>();double[] low,high;
        Hull(MapDefinition.Part part){w=part.w;h=part.h;d=part.d;yaw=part.yaw;shape=part.shape;
            MapDefinition.Part local=part.copy();local.x=local.y=local.z=0;
            java.util.List<double[][]> faces=MapMeshes.faces(local);double[][] basis={{1,0,0},{0,1,0},{0,0,1}};
            for(double[] axis:basis)add(axis);
            for(double[][] face:faces){for(int i=1;i+1<face.length;i++)add(cross(sub(face[i],face[0]),sub(face[i+1],face[0])));
                for(int i=0;i<face.length;i++){double[] edge=sub(face[(i+1)%face.length],face[i]);for(double[] axis:basis)add(cross(edge,axis));}}
            low=new double[axes.size()];high=new double[axes.size()];java.util.Arrays.fill(low,Double.POSITIVE_INFINITY);java.util.Arrays.fill(high,Double.NEGATIVE_INFINITY);
            for(int i=0;i<axes.size();i++)for(double[][] face:faces)for(double[] vertex:face){double v=dot(axes.get(i),vertex);low[i]=Math.min(low[i],v);high[i]=Math.max(high[i],v);}
        }
        void add(double[] axis){double length=Math.sqrt(dot(axis,axis));if(length<1e-10)return;for(int i=0;i<3;i++)axis[i]/=length;
            for(double[] old:axes)if(Math.abs(dot(old,axis))>1-1e-9)return;axes.add(axis);}
        boolean matches(MapDefinition.Part p){return w==p.w&&h==p.h&&d==p.d&&yaw==p.yaw&&shape==p.shape;}
    }
    private static Hull hull(MapDefinition.Part p){synchronized(HULLS){Hull h=HULLS.get(p);if(h==null||!h.matches(p)){h=new Hull(p);HULLS.put(p,h);}return h;}}
    private static final class Hit {double time=Double.POSITIVE_INFINITY,penetration;double[] normal;}
    private static Hit hit(MapDefinition.Part p,double[] center,double[] motion,double radius,double height){
        // Broad phase uses swept actor bounds; it cannot miss thin surfaces.
        double[] bounds=p.bounds();for(int i=0;i<3;i++){double extent=i==1?height/2:radius;if(Math.max(center[i],center[i]+motion[i])+extent<bounds[i]-EPS||Math.min(center[i],center[i]+motion[i])-extent>bounds[i+3]+EPS)return null;}
        Hull h=hull(p);double[] local={center[0]-p.x,center[1]-p.y,center[2]-p.z};
        double enter=-Double.MAX_VALUE,exit=Double.MAX_VALUE,minPush=Double.POSITIVE_INFINITY;double[] normal=null,push=null;boolean inside=true;
        for(int i=0;i<h.axes.size();i++){double[] n=h.axes.get(i);double extent=radius*(Math.abs(n[0])+Math.abs(n[2]))+height/2*Math.abs(n[1]);
            double lo=h.low[i]-extent,hi=h.high[i]+extent,pos=dot(local,n),velocity=dot(motion,n);
            if(pos<=lo+EPS||pos>=hi-EPS)inside=false;
            double left=pos-lo,right=hi-pos;
            if(left>EPS&&left<minPush&&center[1]-n[1]*(left+EPS)>=height/2-EPS){minPush=left;push=new double[]{-n[0],-n[1],-n[2]};}
            if(right>EPS&&right<minPush&&center[1]+n[1]*(right+EPS)>=height/2-EPS){minPush=right;push=n;}
            if(Math.abs(velocity)<1e-12){if(pos<=lo+EPS||pos>=hi-EPS)return null;continue;}
            double t0=(lo-pos)/velocity,t1=(hi-pos)/velocity;double sign=-1;if(t0>t1){double swap=t0;t0=t1;t1=swap;sign=1;}
            if(t0>enter){enter=t0;normal=new double[]{n[0]*sign,n[1]*sign,n[2]*sign};}exit=Math.min(exit,t1);
        }
        Hit result=new Hit();
        if(inside&&push!=null){result.time=0;result.penetration=minPush+EPS;result.normal=push;return result;}
        if(enter>exit+EPS||exit<0||enter< -EPS||enter>1||normal==null||dot(motion,normal)>=-1e-12)return null;
        result.time=Math.max(0,enter);result.normal=normal;return result;
    }
    static double[] move(MapDefinition map,double ox,double oy,double oz,double x,double y,double z,double radius,double height){return move(map,ox,oy,oz,x,y,z,radius,height,null);}
    private static double[] move(MapDefinition map,double ox,double oy,double oz,double x,double y,double z,double radius,double height,MapDefinition.Part ignore){
        double[] center={ox,Math.max(0,oy)+height/2,oz},motion={x-ox,y-oy,z-oz};
        for(int iteration=0;iteration<24;iteration++){
            Hit nearest=null;
            for(MapDefinition.Part p:map.objects)if(p!=ignore&&p.collision){Hit h=hit(p,center,motion,radius,height);if(h!=null&&(nearest==null||h.penetration>0&&nearest.penetration==0||h.penetration==0&&nearest.penetration==0&&h.time<nearest.time))nearest=h;}
            if(nearest!=null&&nearest.penetration>0){for(int i=0;i<3;i++)center[i]+=nearest.normal[i]*nearest.penetration;continue;}
            if(motion[1]<0&&center[1]+motion[1]<height/2){double t=(height/2-center[1])/motion[1];if(nearest==null||t<nearest.time){nearest=new Hit();nearest.time=Math.max(0,t);nearest.normal=new double[]{0,1,0};}}
            if(nearest==null){for(int i=0;i<3;i++)center[i]+=motion[i];break;}
            for(int i=0;i<3;i++){center[i]+=motion[i]*nearest.time;motion[i]*=1-nearest.time;}
            double inward=dot(motion,nearest.normal);if(inward<0)for(int i=0;i<3;i++)motion[i]-=nearest.normal[i]*inward;
            // A tiny outward bias prevents numerical re-entry without visible gaps.
            for(int i=0;i<3;i++)center[i]+=nearest.normal[i]*EPS;
        }
        double[] edge=boundary(map,center[0],center[2],radius);double[] result={edge[0],Math.max(0,center[1]-height/2),edge[1]},target={x,y,z};
        // Preserve unchanged coordinates exactly: callers use differences to detect landings/bounces.
        for(int i=0;i<3;i++)if(Math.abs(result[i]-target[i])<1e-10&&(i!=1||target[i]>=0))result[i]=target[i];
        return result;
    }
    static void tick(MapDefinition map,double dt){
        double remaining=Math.min(.1,Math.max(0,dt));
        while(remaining>1e-8){double step=Math.min(.01,remaining);remaining-=step;
            for(MapDefinition.Part p:map.objects)if(!p.anchored){
                p.vy-=18*step;double nx=p.x+p.vx*step,ny=p.y+p.vy*step,nz=p.z+p.vz*step;
                double[] b=move(map,p.x,p.y,p.z,nx,ny,nz,Math.max(p.w,p.d)/2,p.h,p);
                if(b[0]!=nx)p.vx*=-.2;if(b[2]!=nz)p.vz*=-.2;
                if(b[1]!=ny){p.vy=Math.abs(p.vy)>.8?-p.vy*.2:0;p.vx*=Math.exp(-7*step);p.vz*=Math.exp(-7*step);}
                p.x=b[0];p.y=Math.min(200,b[1]);p.z=b[2];
            }
        }
    }
}
