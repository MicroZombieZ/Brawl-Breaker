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
    static Color shade(Color c,double[][] v){double ax=v[1][0]-v[0][0],ay=v[1][1]-v[0][1],az=v[1][2]-v[0][2],bx=v[2][0]-v[0][0],by=v[2][1]-v[0][1],bz=v[2][2]-v[0][2];double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,len=Math.sqrt(nx*nx+ny*ny+nz*nz);double light=len<1e-10?.65:.45+.55*Math.abs((nx*.3+ny*.8+nz*.5)/(len*Math.sqrt(.98)));return new Color((int)(c.getRed()*light),(int)(c.getGreen()*light),(int)(c.getBlue()*light),c.getAlpha());}
}
