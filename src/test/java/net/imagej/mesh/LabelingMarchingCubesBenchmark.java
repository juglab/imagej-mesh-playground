package net.imagej.mesh;

import net.imagej.ImageJ;
import net.imagej.ops.geom.geom3d.RegionMarchingCubes;
import net.imagej.ops.geom.geom3d.SmoothedRegionMarchingCubes;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.roi.labeling.ImgLabeling;
import net.imglib2.roi.labeling.LabelRegion;
import net.imglib2.roi.labeling.LabelRegions;
import net.imglib2.roi.labeling.LabelingType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@State( Scope.Thread )
@Fork( 1 )
public class LabelingMarchingCubesBenchmark
{
	private File exportDir = new File("/home/random/Development/imagej/project/imagej-mesh-playground/results/");

	ImgFactory<FloatType> floatFactory = new ArrayImgFactory<>(new FloatType());
	private ImgLabeling<IntType, IntType> labeling;
	private ImageJ ij = new ImageJ();
	private ArrayImg<IntType, IntArray> img;
	private IntType label1;
	private IntType label2;
	private LabelRegion<IntType> region1;
	private LabelRegion<IntType> region2;

	@Setup
	public void setup() {
		img = ArrayImgs.ints( 1000, 1000, 100 );
		labeling = new ImgLabeling<>(img);
		RandomAccess<LabelingType<IntType>> ra = labeling.randomAccess();
		label1 = new IntType(1);
		label2 = new IntType(2);
		ra.setPosition(0, 2);
		for (float x = 0; x < 3; x+=0.001) {
			int posx = (int) (Math.sin(x) * 400 + 400);
			int posy = (int) (Math.cos(x) * 400 + 400);
			int posz = (int) (Math.sin(x) * 40 + 40);
			ra.setPositionAndGet(posx, posy, posz).add(label1);
			ra.setPositionAndGet(posx+1, posy, posz).add(label1);
			ra.setPositionAndGet(posx, posy+1, posz).add(label1);
			ra.setPositionAndGet(posx+1, posy+1, posz).add(label1);
			ra.setPositionAndGet(posx, posy, posz+1).add(label1);
			ra.setPositionAndGet(posx+1, posy, posz+1).add(label1);
			ra.setPositionAndGet(posx, posy+1, posz+1).add(label1);
			ra.setPositionAndGet(posx+1, posy+1, posz+1).add(label1);
		}
		for (int x = 800; x < 820; x++) {
			for (int y = 380; y < 470; y++) {
				for (int z = 70; z < 80; z++) {
					ra.setPositionAndGet(x, y, z).add(label2);
				}
			}
		}
		LabelRegions<IntType> labelRegions = new LabelRegions<>(labeling);
		region1 = labelRegions.getLabelRegion(label1);
		region2 = labelRegions.getLabelRegion(label2);
	}

	@Benchmark
	@BenchmarkMode( Mode.AverageTime )
	@OutputTimeUnit( TimeUnit.MILLISECONDS )
	public List<Mesh> calculateDefault()
	{
		List<Mesh> res = new ArrayList<>();
		res.add(ij.op().geom().marchingCubes(region1));
		res.add(ij.op().geom().marchingCubes(region2));
		return res;
	}


	@Benchmark
	@BenchmarkMode( Mode.AverageTime )
	@OutputTimeUnit( TimeUnit.MILLISECONDS )
	public List<Mesh> calculateRegions() {
		List<Mesh> res = new ArrayList<>();
		res.add(new RegionMarchingCubes().calculate(region1));
		res.add(new RegionMarchingCubes().calculate(region2));
		return res;
	}

	@Benchmark
	@BenchmarkMode( Mode.AverageTime )
	@OutputTimeUnit( TimeUnit.MILLISECONDS )
	public List<Mesh> calculateSmooth() {
		List<Mesh> res = new ArrayList<>();
		Arrays.asList(label1, label2).forEach(label -> {
			LabelRegion<IntType> region = label == label1 ? region1 : region2;
			RandomAccessibleInterval<FloatType> smoothed = floatFactory.create(Intervals.expand(region, 4));
			smoothed = Views.translate(smoothed, region.min(0)-4, region.min(1)-4, region.min(2)-4);
			ij.op().filter().gauss(smoothed, Views.expandZero(region, 4, 4, 4), 2);
//			ij.ui().show(label + " smoothed", Views.zeroMin(smoothed));
			double isolevel = label == label1? 0.13 : 0.5;
			res.add(new SmoothedRegionMarchingCubes().calculate(region, smoothed, isolevel));
		});
		return res;
	}

	private void displayResult() {
		ij.launch();
		setup();
//		ij.ui().show(img);
		calculateAndExport(calculateDefault(), "default");
		calculateAndExport(calculateRegions(), "region");
		calculateAndExport(calculateSmooth(), "smooth");
	}

	private void calculateAndExport(List<Mesh> meshes, String subdirName) {
		System.out.println("---" + subdirName + "---");
		for (int i = 0; i < meshes.size(); i++) {
			Mesh mesh = meshes.get(i);
			System.out.println("Mesh " + i + " with " + mesh.vertices().size() + " vertices and " + mesh.triangles().size() + " triangles.");
			new MeshPlyExporter().export(String.valueOf(i), new File(exportDir, subdirName), mesh);
		}
	}

	private static void doBenchmarks() throws RunnerException {
		final Options opt = new OptionsBuilder()
				.include( LabelingMarchingCubesBenchmark.class.getSimpleName() )
				.warmupIterations( 4 )
				.measurementIterations( 8 )
				.warmupTime( TimeValue.milliseconds( 1000 ) )
				.measurementTime( TimeValue.milliseconds( 1000 ) )
				.build();
		new Runner( opt ).run();
	}

	public static void main( final String... args ) throws RunnerException
	{
		doBenchmarks();
		new LabelingMarchingCubesBenchmark().displayResult();
	}
}
