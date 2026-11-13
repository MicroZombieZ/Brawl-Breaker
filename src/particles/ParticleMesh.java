import java.io.*;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Immutable, bounded triangle mesh embedded in particle effects. */
final class ParticleMesh {
    static final int MAX_BYTES=262144, MAX_VERTICES=512, MAX_TRIANGLES=256;
    final double[][] vertices;
    final int[][] triangles;
    ParticleMesh(double[][] vertices,int[][] triangles) throws IOException {
        if(vertices.length<3||vertices.length>MAX_VERTICES||triangles.length<1||triangles.length>MAX_TRIANGLES)
            throw new IOException("Particle shapes need 3-512 vertices and 1-256 triangles.");
        this.vertices=new double[vertices.length][];this.triangles=new int[triangles.length][];
        for(int i=0;i<vertices.length;i++){this.vertices[i]=vertices[i].clone();for(double v:vertices[i])
            if(!Double.isFinite(v)||Math.abs(v)>1000000)throw new IOException("Invalid vertex coordinate.");}
        for(int i=0;i<triangles.length;i++){this.triangles[i]=triangles[i].clone();for(int v:triangles[i])
            if(v<0||v>=vertices.length)throw new IOException("Face references a missing vertex.");}
    }
    static ParticleMesh read(Path path) throws IOException {
        try(InputStream in=Files.newInputStream(path)){return read(in);}
    }
    static ParticleMesh read(InputStream in) throws IOException {
        byte[] data=in.readNBytes(MAX_BYTES+1);if(data.length>MAX_BYTES)throw new IOException("OBJ must be under 256 KB. Use a low-poly particle shape.");
        ArrayList<double[]> vertices=new ArrayList<>();ArrayList<int[]> faces=new ArrayList<>();int lineNumber=0;
        try {
            for(String line:new String(data,StandardCharsets.UTF_8).split("\\R")){
                lineNumber++;line=line.split("#",2)[0].trim();if(line.isEmpty())continue;String[] words=line.split("\\s+");
                if(words[0].equals("v")){
                    if(words.length<4)throw new IllegalArgumentException();
                    vertices.add(new double[]{Double.parseDouble(words[1]),Double.parseDouble(words[2]),Double.parseDouble(words[3])});
                    if(vertices.size()>MAX_VERTICES)throw new IOException("Maximum 512 vertices per particle shape.");
                }else if(words[0].equals("f")){
                    if(words.length!=4)throw new IOException("Triangulate faces before importing (OBJ line "+lineNumber+").");
                    int[] face=new int[3];for(int i=0;i<3;i++){int n=Integer.parseInt(words[i+1].split("/",-1)[0]);face[i]=n<0?vertices.size()+n:n-1;}
                    faces.add(face);if(faces.size()>MAX_TRIANGLES)throw new IOException("Maximum 256 triangles per particle shape.");
                }
            }
            return new ParticleMesh(vertices.toArray(new double[0][]),faces.toArray(new int[0][]));
        }catch(RuntimeException ex){throw new IOException("Invalid OBJ at line "+lineNumber+".",ex);}
    }
    String obj(){StringBuilder s=new StringBuilder("# Particle Studio shape\n");for(double[] v:vertices)s.append("v ").append(v[0]).append(' ').append(v[1]).append(' ').append(v[2]).append('\n');
        for(int[] f:triangles)s.append("f ").append(f[0]+1).append(' ').append(f[1]+1).append(' ').append(f[2]+1).append('\n');return s.toString();}
    ParticleMesh normalize() throws IOException {
        double[] low={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},high={-Double.MAX_VALUE,-Double.MAX_VALUE,-Double.MAX_VALUE};
        for(double[] v:vertices)for(int i=0;i<3;i++){low[i]=Math.min(low[i],v[i]);high[i]=Math.max(high[i],v[i]);}
        double extent=Math.max(high[0]-low[0],Math.max(high[1]-low[1],high[2]-low[2]));if(extent<1e-9)throw new IOException("Shape has no size.");
        double[][] out=new double[vertices.length][3];for(int j=0;j<out.length;j++)for(int i=0;i<3;i++)out[j][i]=(vertices[j][i]-(low[i]+high[i])/2)/extent;
        return new ParticleMesh(out,triangles);
    }
    ParticleMesh transform(double sx,double sy,double sz,double rx,double ry,double rz) throws IOException {
        double[][] out=new double[vertices.length][3];rx=Math.toRadians(rx);ry=Math.toRadians(ry);rz=Math.toRadians(rz);
        for(int i=0;i<out.length;i++){double x=vertices[i][0]*sx,y=vertices[i][1]*sy,z=vertices[i][2]*sz;
            double a=y*Math.cos(rx)-z*Math.sin(rx);z=y*Math.sin(rx)+z*Math.cos(rx);y=a;
            a=x*Math.cos(ry)+z*Math.sin(ry);z=-x*Math.sin(ry)+z*Math.cos(ry);x=a;
            out[i]=new double[]{x*Math.cos(rz)-y*Math.sin(rz),x*Math.sin(rz)+y*Math.cos(rz),z};}
        return new ParticleMesh(out,triangles);
    }
    double[][] face(int[] f,double x,double y,double z,double size,double angle){double[][] out=new double[3][3];
        for(int i=0;i<3;i++){double[] v=vertices[f[i]];out[i]=new double[]{x+size*(v[0]*Math.cos(angle)+v[2]*Math.sin(angle)),y+size*v[1],z+size*(-v[0]*Math.sin(angle)+v[2]*Math.cos(angle))};}return out;}
    static ParticleMesh cube(){try{return new ParticleMesh(new double[][]{{-.5,-.5,-.5},{.5,-.5,-.5},{.5,.5,-.5},{-.5,.5,-.5},{-.5,-.5,.5},{.5,-.5,.5},{.5,.5,.5},{-.5,.5,.5}},
        new int[][]{{0,1,2},{0,2,3},{4,6,5},{4,7,6},{0,4,5},{0,5,1},{3,2,6},{3,6,7},{0,3,7},{0,7,4},{1,5,6},{1,6,2}});}catch(IOException ex){throw new AssertionError(ex);}}
    enum Primitive { CUBE, SPHERE, CYLINDER, CONE, TRAPEZOID, PYRAMID }
    static ParticleMesh primitive(Primitive type){
        if(type==Primitive.CUBE)return cube();
        try{
            if(type==Primitive.TRAPEZOID){ParticleMesh c=cube();double[][] v=new double[c.vertices.length][];
                for(int i=0;i<v.length;i++){v[i]=c.vertices[i].clone();if(v[i][1]>0)v[i][0]*=.5;}
                return new ParticleMesh(v,c.triangles);
            }
            ArrayList<double[]> v=new ArrayList<>();ArrayList<int[]> f=new ArrayList<>();
            int n=type==Primitive.PYRAMID?4:12;
            if(type==Primitive.SPHERE){
                v.add(new double[]{0,.5,0});int rings=7;
                for(int r=1;r<=rings;r++){double a=Math.PI*r/(rings+1);for(int j=0;j<n;j++){double b=2*Math.PI*j/n;v.add(new double[]{.5*Math.sin(a)*Math.cos(b),.5*Math.cos(a),.5*Math.sin(a)*Math.sin(b)});}}
                int bottom=v.size();v.add(new double[]{0,-.5,0});
                for(int j=0;j<n;j++){int next=(j+1)%n;f.add(new int[]{0,1+j,1+next});f.add(new int[]{bottom,1+(rings-1)*n+next,1+(rings-1)*n+j});}
                for(int r=0;r<rings-1;r++)for(int j=0;j<n;j++){int a=1+r*n+j,b=1+r*n+(j+1)%n;f.add(new int[]{a,a+n,b});f.add(new int[]{b,a+n,b+n});}
            }else{
                for(int j=0;j<n;j++){double a=2*Math.PI*j/n+(type==Primitive.PYRAMID?Math.PI/4:0);v.add(new double[]{.5*Math.cos(a),-.5,.5*Math.sin(a)});}
                int base=v.size();v.add(new double[]{0,-.5,0});
                if(type==Primitive.CYLINDER){int top=v.size();for(int j=0;j<n;j++){double[] b=v.get(j);v.add(new double[]{b[0],.5,b[2]});}int center=v.size();v.add(new double[]{0,.5,0});
                    for(int j=0;j<n;j++){int k=(j+1)%n;f.add(new int[]{base,k,j});f.add(new int[]{center,top+j,top+k});f.add(new int[]{j,k,top+j});f.add(new int[]{k,top+k,top+j});}
                }else{int tip=v.size();v.add(new double[]{0,.5,0});for(int j=0;j<n;j++){int k=(j+1)%n;f.add(new int[]{base,k,j});f.add(new int[]{j,k,tip});}}
            }
            return new ParticleMesh(v.toArray(new double[0][]),f.toArray(new int[0][]));
        }catch(IOException ex){throw new AssertionError(ex);}
    }
    static Color shade(Color c,double[][] v){double ax=v[1][0]-v[0][0],ay=v[1][1]-v[0][1],az=v[1][2]-v[0][2],bx=v[2][0]-v[0][0],by=v[2][1]-v[0][1],bz=v[2][2]-v[0][2];double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,len=Math.sqrt(nx*nx+ny*ny+nz*nz);double light=len<1e-10?.65:.45+.55*Math.abs((nx*.3+ny*.8+nz*.5)/(len*Math.sqrt(.98)));return new Color((int)(c.getRed()*light),(int)(c.getGreen()*light),(int)(c.getBlue()*light),c.getAlpha());}
}
