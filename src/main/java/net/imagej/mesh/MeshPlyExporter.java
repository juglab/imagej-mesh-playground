package net.imagej.mesh;

import net.imglib2.RealPoint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class MeshPlyExporter {

	private int faceCount;
	private int vertexCount;

	private void reset() {
		faceCount = 0;
		vertexCount = 0;
	}

	public void export(String name, File exportDir, Mesh mesh) {
		reset();
		try {
			File plyFile = new File(exportDir, name + ".ply");
			File tmpFacesFile = Files.createTempFile("faces", ".ply").toFile();
			File tmpVerticesFile = Files.createTempFile("vertices", ".ply").toFile();
			plyFile.delete();
			plyFile.createNewFile();
			try (PrintWriter plyWriter = new PrintWriter(new FileOutputStream(plyFile, true));
			     PrintWriter facesWriter = new PrintWriter(new FileOutputStream(tmpFacesFile, true));
			     PrintWriter verticesWriter = new PrintWriter(new FileOutputStream(tmpVerticesFile, true))) {

				plyWriter.append("ply\n");
				plyWriter.append("format ascii 1.0\n");
				plyWriter.append("comment ").append(name).append("\n");

				writeMesh(mesh, facesWriter, verticesWriter);

				plyWriter.append("element vertex ").append(String.valueOf(vertexCount)).append("\n");
				plyWriter.append("property float x\n");
				plyWriter.append("property float y\n");
				plyWriter.append("property float z\n");
				plyWriter.append("element face ").append(String.valueOf(faceCount)).append("\n");
				plyWriter.append("property list uchar int vertex_index\n");
				plyWriter.append("end_header\n");
				plyWriter.flush();
				appendFile(tmpVerticesFile, plyWriter);
				plyWriter.flush();
				appendFile(tmpFacesFile, plyWriter);

			}
			System.out.println("Exported mesh " + name + " with " + vertexCount + " vertices and " + faceCount + " faces to " + plyFile.getAbsolutePath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void appendFile(File file, PrintWriter writer) {
		Scanner sc = null;
		try {
			String lineSeparator = System.getProperty("line.separator");
			sc = new Scanner(file);
			while(sc.hasNextLine()) {
				String s = sc.nextLine()+lineSeparator;
				writer.write(s);
			}
		}
		catch(IOException ioe) {
			System.out.println(ioe);
		} finally {
			if(sc != null) {
				sc.close();
			}
			if(writer != null) {
				writer.flush();
			}
		}
	}

	private void writeMesh(Mesh mesh, PrintWriter facesWriter, PrintWriter verticesWriter ) {
		StringBuilder faces = new StringBuilder();
		StringBuilder vertices = new StringBuilder();
		Map<String, MyVertex> myVertices = new LinkedHashMap<>();
		for (Triangle triangle : mesh.triangles()) {
			RealPoint p1 = new RealPoint(triangle.v0x(), triangle.v0y(), triangle.v0z());
			RealPoint p2 = new RealPoint(triangle.v1x(), triangle.v1y(), triangle.v1z());
			RealPoint p3 = new RealPoint(triangle.v2x(), triangle.v2y(), triangle.v2z());
			int i1 = getVertex(myVertices, p1).index;
			int i2 = getVertex(myVertices, p2).index;
			int i3 = getVertex(myVertices, p3).index;
			if(i1 == i2 || i2 == i3 || i1 == i3) continue;
			faces.append("3 ").append(i1)
					.append(" ").append(i2)
					.append(" ").append(i3).append("\n");
			faceCount++;
		}

		for (MyVertex vertex : myVertices.values()) {
			appendVertex(vertices, vertex.point);
			vertexCount++;
		}

		myVertices.clear();

		facesWriter.print(faces);
		verticesWriter.print(vertices);
		facesWriter.flush();
		verticesWriter.flush();
	}

	private MyVertex makeMyVertex(Map<String, MyVertex> vertices, String hash, RealPoint point) {
		MyVertex vertex = new MyVertex();
		vertex.point = point;
		vertex.index = vertices.size();
		vertices.put(hash, vertex);
		return vertex;
	}

	private MyVertex getVertex(Map<String, MyVertex> vertices, RealPoint point) {
		String hash = getHash(point);
		MyVertex vertex = vertices.get(hash);
		if(vertex == null) vertex = makeMyVertex(vertices, hash, point);
		return vertex;
	}

	private String getHash(RealPoint point) {
		return (int)(point.getFloatPosition(0)*100) + "-"
				+ (int)(point.getFloatPosition(1)*100) + "-"
				+ (int)(point.getFloatPosition(2)*100);
	}

	private void appendVertex(StringBuilder vertices, RealPoint point) {
		vertices.append(point.getFloatPosition(0)).append(" ")
				.append(point.getFloatPosition(1)).append(" ")
				.append(point.getFloatPosition(2)).append("\n");
	}

	private static class MyVertex {
		int index;
		RealPoint point;
	}
}
