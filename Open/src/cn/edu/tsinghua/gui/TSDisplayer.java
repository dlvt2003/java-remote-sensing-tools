/**
 * 
 */
package cn.edu.tsinghua.gui;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math.distribution.NormalDistributionImpl;
import org.apache.commons.math.random.RandomDataImpl;
import org.apache.commons.math.stat.StatUtils;

import com.berkenviro.gis.GISUtils;
import com.berkenviro.imageprocessing.GDALUtils;
import com.berkenviro.imageprocessing.JAIUtils;
import com.berkenviro.imageprocessing.SplineFunction;
import com.sun.media.jai.widget.DisplayJAI;

import cn.edu.tsinghua.timeseries.DuchonSplineFunction;
import cn.edu.tsinghua.timeseries.Extremum;
import cn.edu.tsinghua.timeseries.ImageLoadr2;
import cn.edu.tsinghua.timeseries.TSUtils;

import ru.sscc.spline.Spline;
import ru.sscc.spline.polynomial.PSpline;

import JSci.awt.DefaultGraph2DModel;
import JSci.awt.Graph2D;
import JSci.awt.DefaultGraph2DModel.DataSeries;
import JSci.swing.JLineGraph;

/**
 * @author Nicholas Clinton
 *
 */
public class TSDisplayer extends JFrame implements ActionListener {

	private JTextArea stats;
	private JLineGraph graph;
	private DefaultGraph2DModel model;
    private Button splineButton = new Button("Spline");
    private Button smoothButton = new Button("Smooth");
    private Button resetButton = new Button("Reset");
    private Button calcButton = new Button("Calculate");
    private Button meanButton = new Button("Mean");
    private Button ratioButton = new Button("Ratio");
    private JCheckBox polyBox = new JCheckBox("Polynomial Spline");
    private int numSeries;
    private double[][] series;
    private UnivariateRealFunction spline;
    private double[][] smooth;
    private PolynomialSplineFunction derivative;
    
    /**
     * 
     * @param minX
     * @param maxX
     * @param minY
     * @param maxY
     */
    public TSDisplayer() {
    	super("Time series display");
   	 	
    	// graph
   	 	model = new DefaultGraph2DModel();
   	 	DefaultGraph2DModel.DataSeries series = new DefaultGraph2DModel.DataSeries(new double[] {0}, new double[] {0});
   	 	model.addSeries(series); // dummy series
   	 	numSeries = 1;
   	 	graph = new JLineGraph(model);
   	 	graph.setGridLines(true);
        graph.setMarker(new Graph2D.DataMarker.Circle(3));
//        NumberFormat format = NumberFormat.getInstance();
//        format.setMaximumIntegerDigits(1);
//        format.setMinimumFractionDigits(2);
//        graph.setYNumberFormat(format);
        //graph.setYExtrema(0.0f, 0.6f);
        //graph.setYIncrement(0.1f);

        // stats
        stats = new JTextArea(10,20);

        // buttons
        Panel buttonPanel = new Panel();
        buttonPanel.add(splineButton);
        splineButton.addActionListener(this);
        buttonPanel.add(smoothButton);
        smoothButton.addActionListener(this);
        buttonPanel.add(resetButton);
        resetButton.addActionListener(this);
        buttonPanel.add(calcButton);
        calcButton.addActionListener(this);
        buttonPanel.add(meanButton);
        meanButton.addActionListener(this);
        buttonPanel.add(ratioButton);
        ratioButton.addActionListener(this);
        polyBox = new JCheckBox("Polynomial Spline"); 
        polyBox.setSelected(false);
        buttonPanel.add(polyBox);
        add(buttonPanel,"North");
        
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(buttonPanel, BorderLayout.NORTH);
        getContentPane().add(graph, BorderLayout.CENTER);
        getContentPane().add(stats, BorderLayout.SOUTH);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setVisible(true);
    }
    
    
	/**
	 * Start over.
	 * @param series
	 */
	public void graphSeries(double[][] inSeries) {
		series = inSeries;
		// clear the text
		stats.setText("");
		// clear series, if there are any
		while(numSeries>1) {
			model.removeSeries(numSeries-1);
			numSeries--;
		}
		DefaultGraph2DModel.DataSeries dataSeries = new DefaultGraph2DModel.DataSeries(series[0], series[1]);
		model.changeSeries(0, dataSeries);
		smooth = null;
		spline = null;
		derivative = null;
		graph.redraw();
		repaint();
	}
    
	/**
	 * 
	 * @param series
	 */
	public void addSeries(double[][] series) {
		model.addSeries(new DataSeries(series[0], series[1]));
		numSeries++;
		graph.redraw();
		repaint();
	}
    
	/**
	 * 
	 */
	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource() == resetButton) {
			while (numSeries>1) {
				model.removeSeries(numSeries-1);
				numSeries--;
			}
			graph.redraw();
			repaint();
		}
		else if (e.getSource() == splineButton) {
			fitSpline();
		}
		else if (e.getSource() == smoothButton) {
			smooth();
		}
		else if (e.getSource() == calcButton) {
			try {
				calcStats();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
		else if (e.getSource() == meanButton) {
			try {
				calcMean();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
		else if (e.getSource() == ratioButton) {
			try {
				calcRatio();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}
	
	/**
	 * Thin-plate spline
	 */
	private void fitSpline() {
		try {
			// polynomial spline, commons math impl
			if (polyBox.isSelected()) {
				SplineFunction polySpline = new SplineFunction(series);
				spline = polySpline;
		        addSpline(polySpline);
		        // first derivative
//	            PolynomialSplineFunction derivative = (PolynomialSplineFunction) polySpline.derivative();
//	            addSpline(derivative);
//	            // second derivative
//	            UnivariateRealFunction secDerivative = derivative.derivative();
//	            addSpline(secDerivative);
			} 
			// Duchon spline, JSpline+ impl
			else {
				// splines on the original data, un-scaled
				DuchonSplineFunction dSpline = new DuchonSplineFunction(series);
				spline = dSpline;
				double[] minMax = getXRange();
		        addSpline(dSpline);
				// first derivative
//		        SplineFunction pSpline = new SplineFunction(TSUtils.splineValues(dSpline, minMax, 500));
//		        PolynomialSplineFunction derivative = (PolynomialSplineFunction)pSpline.derivative();
//            	addSpline(derivative);
//            	// second derivative
//            	UnivariateRealFunction secDerivative = derivative.derivative();
//	            addSpline(secDerivative);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Smooth a spline with two methods
	 * @param spline
	 */
	private void smooth() {
		try {
			double[] minMax = getXRange();
			// smooth with an FFT
			smooth = TSUtils.smoothFunction(spline, minMax[0], minMax[1], 0.85);
			addSeries(smooth);
			// first derivative 
			SplineFunction smoothed = new SplineFunction(smooth);
	        derivative = (PolynomialSplineFunction)smoothed.derivative();
	        //double[] derivRange = new double[] {StatUtils.min(smooth[0]), StatUtils.max(smooth[0])};
        	//addSpline(derivative, derivRange);
        	// second derivative
        	//UnivariateRealFunction secDerivative = derivative.derivative();
            //addSpline(secDerivative, derivRange);
			// smooth with Savitzky-Golay, just for comparison
			double[][] interpolated = TSUtils.splineValues(spline, minMax, 500);
			double[][] smooth2 = TSUtils.sgSmooth(interpolated, 5, 1);
			addSeries(smooth2);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Graph a Spline or a SplineFunction
	 * @param spline
	 */
	public void addSpline(UnivariateRealFunction spline) {
		addSpline(spline, getXRange());
	}
	
	/**
	 * 
	 * @param spline
	 * @param range
	 */
	public void addSpline(UnivariateRealFunction spline, double[] range) {
		double[][] splineVals = null;
		try {
			splineVals = TSUtils.splineValues(spline, range, 500);
		} catch (Exception e) {
			e.printStackTrace();
		}
		addSeries(splineVals);
	}
	
	/**
	 * Get the range of the initial series
	 * @return
	 */
	private double[] getXRange() {
		return new double[] {StatUtils.min(series[0]), StatUtils.max(series[0])};
	}
	
	/**
	 * Get the range of the initial series
	 * @return
	 */
	private double[] getYRange() {
		return new double[] {StatUtils.min(series[1]), StatUtils.max(series[1])};
	}
	
	/**
	 * 
	 * @param text
	 */
	public void addText(String text) {
		stats.append(text+"\n");
		this.repaint();
	}
	
	/*
	 * This method calculates various phenology stats from the 
	 * Spline instance variable.
	 */
	private void calcStats() throws Exception {
		if (spline != null) {
			//stats.append("Roots: \n");
			// set up the display strings
			//String roots = "{";
			String maxima = "{";
			String minima = "{";
			// get the List of extrema from the SMOOTH derivative
			List<Extremum> extrema = TSUtils.getExtrema(derivative.derivative(), getXRange());
			for (Extremum e : extrema) {
				//roots += (String.format("%.2f", e.getX())+", ");
				if (e.getType() == Extremum.EXTREMUM_TYPE_MAX) {
					maxima += (String.format("%.2f", e.getX())+", ");
				}
				else {
					minima += (String.format("%.2f", e.getX())+", ");
				}
			}
			
			// finish the strings and append
			//roots = roots.substring(0, roots.length()-2) + "}";
			if (maxima.length() > 1) {
				maxima = maxima.substring(0, maxima.length()-2) + "}";
			}
			else { maxima+="}"; }
			if (minima.length() > 1) {
				minima = minima.substring(0, minima.length()-2) + "}";
			}
			else { minima+="}"; }
			
			//stats.append(roots + "\n");
			stats.append("Inflection point (increasing): \n");
			stats.append(maxima + "\n");
			stats.append("Inflection point (decreasing): \n");
			stats.append(minima + "\n");
			
//			// calculate and display the largest two fluctuations
//			stats.append("MinX1, MinY1, MaxX1, MaxY1, MinX2, MinY2, MaxX2, MaxY2} \n");
//			double[] extremaArr = null;
//			// Evaluate SMOOTH data values
//			extremaArr = TSUtils.evaluateExtrema(extrema, new SplineFunction(smooth), getXRange());
//			String fluctuations = "";
//			for (int j=0; j<extremaArr.length; j++) {
//				fluctuations+= (String.format("%.2f", extremaArr[j])+", ");
//			}
//			stats.append(fluctuations+"\n");
			this.repaint();
		}
	}
	
	/*
	 * This method adds the running mean to the graph and reports the
	 * Reed et al. onset of greenness metric.
	 */
	private void calcMean() throws Exception{
		if (spline != null) {
			// just for display
			double[][] mean = TSUtils.runningMean(series, 42);
			addSeries(mean);
			
			double xGreen = TSUtils.greenUpReed(series, getXRange(), 42);
			stats.append("Reed green-up index = "+String.format("%.2f", xGreen)+"\n");
			this.repaint();
		}
	}
	
	/*
	 * This method adds the ratio to the graph and reports the 
	 * White et al. onset of greeness metric.
	 */
	private void calcRatio() throws Exception {
		if (spline != null) {
			// just for display
			double[][] ratio = TSUtils.ndviRatio(smooth, getXRange());
			addSeries(ratio);
			
			double xWhite = TSUtils.greenUpWhite(smooth, getXRange());
			stats.append("White green-up index = "+String.format("%.2f", xWhite)+"\n");
			this.repaint();
		}
	}

	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		final double[][] check = new double[2][200];
		RandomDataImpl gauss = new RandomDataImpl();
		int index = 0;
		double inc = 2.0*Math.PI/100.0;
		for (int t=0; t<200; t++) {
			check[0][t] = t*inc;
			double noise = 
			check[1][t] = 2.0 + Math.cos(check[0][t]) + gauss.nextGaussian(0, 0.3);
		}
		
  		javax.swing.SwingUtilities.invokeLater(new Runnable() {
          public void run() {	
        	  TSDisplayer disp = new TSDisplayer();
        	  disp.graphSeries(check);
          }
  		});
		
	}

}
