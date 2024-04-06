package online.mwang.stockTrading.predict.utils;


import lombok.SneakyThrows;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.io.File;

public class PlotUtil {

    @SneakyThrows
    public static void plot(double[] predicts, double[] actuals, String fileName) {

        double[] index = new double[predicts.length];
        for (int i = 0; i < predicts.length; i++)
            index[i] = i;
        int min = minValue(predicts, actuals);
        int max = maxValue(predicts, actuals);
        final XYSeriesCollection dataSet = new XYSeriesCollection();
        addSeries(dataSet, index, predicts, "Predicts");
        addSeries(dataSet, index, actuals, "Actuals");
        final JFreeChart chart = ChartFactory.createXYLineChart(
                "Prediction Result", // chart title
                "Index", // x axis label
                "Price", // y axis label
                dataSet, // data
                PlotOrientation.VERTICAL,
                true, // include legend
                true, // tooltips
                false // urls
        );
        XYPlot xyPlot = chart.getXYPlot();
//        // X-axis
//        final NumberAxis domainAxis = (NumberAxis) xyPlot.getDomainAxis();
//        domainAxis.setRange((int) index[0], (int) (index[index.length - 1] + 2));
//        domainAxis.setTickUnit(new NumberTickUnit(20));
//        domainAxis.setVerticalTickLabels(true);
//        // Y-axis
//        final NumberAxis rangeAxis = (NumberAxis) xyPlot.getRangeAxis();
//        rangeAxis.setRange(min, max);
//        rangeAxis.setTickUnit(new NumberTickUnit(50));
//		final ChartPanel panel = new ChartPanel(chart);
        // java.awt.HeadlessException: null
        // 添加启动参数：-Djava.awt.headless=false
//		chart.
//		final JFrame f = new JFrame();
//		f.add(panel);
//		f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
//		f.pack();
//		f.setVisible(true);


        JFreeChart xylineChart = ChartFactory.createXYLineChart(
                "NSE-TATAGLOBAL",
                "Days",
                "Price",
                dataSet,
                PlotOrientation.VERTICAL,
                true, true, false);


        int width = 1080;   /* Width of the image */
        int height = 960;  /* Height of the image */
        File XYChart = new File(fileName);
        ChartUtilities.saveChartAsPNG(XYChart, chart, width, height);
    }

    private static void addSeries(final XYSeriesCollection dataSet, double[] x, double[] y, final String label) {
        final XYSeries s = new XYSeries(label);
        for (int j = 0; j < x.length; j++) s.add(x[j], y[j]);
        dataSet.addSeries(s);
    }

    private static int minValue(double[] predicts, double[] actuals) {
        double min = Integer.MAX_VALUE;
        for (int i = 0; i < predicts.length; i++) {
            if (min > predicts[i]) min = predicts[i];
            if (min > actuals[i]) min = actuals[i];
        }
        return (int) (min * 0.98);
    }

    private static int maxValue(double[] predicts, double[] actuals) {
        double max = Integer.MIN_VALUE;
        for (int i = 0; i < predicts.length; i++) {
            if (max < predicts[i]) max = predicts[i];
            if (max < actuals[i]) max = actuals[i];
        }
        return (int) (max * 1.02);
    }

}