/** Camera-only swept sphere against the triangles actually rendered for map objects. */
final class CameraSurfaces {
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    static double hit(double[] start,double[] motion,MapDefinition.Part part,double radius){
        // Bounds only reject distant objects; they never decide a collision.
        double[] b=part.bounds();for(int i=0;i<3;i++)if(Math.max(start[i],start[i]+motion[i])+radius<b[i]||Math.min(start[i],start[i]+motion[i])-radius>b[i+3])return 1;
        double earliest=1;
        for(double[][] face:MapMeshes.faces(part))for(int i=1;i+1<face.length;i++)earliest=Math.min(earliest,triangle(start,motion,face[0],face[i],face[i+1],radius));
        return earliest;
    }
    static double triangle(double[] start,double[] motion,double[] a,double[] b,double[] c,double radius){
        double[] normal=cross(sub(b,a),sub(c,a));double length=Math.sqrt(dot(normal,normal));if(length<1e-10)return 1;
        for(int i=0;i<3;i++)normal[i]/=length;double distance=dot(sub(start,a),normal),speed=dot(motion,normal),best=1;
        if(Math.abs(distance)<=radius&&inside(sub(start,new double[]{normal[0]*distance,normal[1]*distance,normal[2]*distance}),a,b,c))return 0;
        if(Math.abs(speed)>1e-12)for(int sign:new int[]{-1,1}){
            double t=(sign*radius-distance)/speed;if(t<0||t>best)continue;
            double[] point=new double[3];for(int i=0;i<3;i++)point[i]=start[i]+motion[i]*t-normal[i]*sign*radius;
            if(inside(point,a,b,c))best=t;
        }
        for(double[][] edge:new double[][][]{ {a,b},{b,c},{c,a} })best=Math.min(best,edge(start,motion,edge[0],edge[1],radius));
        return best;
    }
    private static boolean inside(double[] p,double[] a,double[] b,double[] c){double[] u=sub(b,a),v=sub(c,a),w=sub(p,a);double uu=dot(u,u),uv=dot(u,v),vv=dot(v,v),wu=dot(w,u),wv=dot(w,v),den=uu*vv-uv*uv;if(den<1e-20)return false;double s=(vv*wu-uv*wv)/den,t=(uu*wv-uv*wu)/den;return s>=-1e-9&&t>=-1e-9&&s+t<=1+1e-9;}
    private static double root(double aa,double bb,double cc){if(cc<=0)return 0;if(aa<1e-20)return 1;double disc=bb*bb-aa*cc;if(disc<0)return 1;double t=(-bb-Math.sqrt(disc))/aa;return t>=0&&t<=1?t:1;}
    private static double edge(double[] start,double[] motion,double[] a,double[] b,double radius){
        double[] axis=sub(b,a),relative=sub(start,a);double len=dot(axis,axis);
        double best=Math.min(root(dot(motion,motion),dot(relative,motion),dot(relative,relative)-radius*radius),sphere(start,motion,b,radius));
        if(len<1e-20)return best;
        double along=dot(relative,axis)/len,velocity=dot(motion,axis)/len;double[] p=new double[3],v=new double[3];for(int i=0;i<3;i++){p[i]=relative[i]-axis[i]*along;v[i]=motion[i]-axis[i]*velocity;}
        double t=root(dot(v,v),dot(p,v),dot(p,p)-radius*radius);double segment=along+velocity*t;
        if(segment>=0&&segment<=1)best=Math.min(best,t);return best;
    }
    private static double sphere(double[] start,double[] motion,double[] center,double radius){double[] p=sub(start,center);return root(dot(motion,motion),dot(p,motion),dot(p,p)-radius*radius);}
}
