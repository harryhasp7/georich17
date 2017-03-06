import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;

public class partialBinningTest {

    long[] queryPoints = { 539289201, 2102587674, 177338570, 1684050323, 1972444342, 1263939584, 2620760160L,
            2264956411L, 1830550771, 1340526882, 977287718, 1445957120, 1031142987, 2196891539L, 1762414965, 1238788844,
            183327226, 2476234227L, 1154008328, 1480326738 };

    private ArrayList<myPoint2> queryPointsCoordinates = new ArrayList<myPoint2>();

    //private List<Long> binCounter = new ArrayList<Long>();

    public static void binning(JavaSparkContext sc, int memoryBudget, String fileName, int type, double selectivity)
            throws IOException {

        long startTime = System.nanoTime();

        JavaRDD<String> inputFile = sc.textFile(fileName); //Spark

        long count = inputFile.count();
        System.out.println("--> total points on file = " + count);
        /*
        JavaRDD<myPoint2> pointData = inputFile.map(new Function<String, myPoint2>() {
            public myPoint2 call(String s) {
                //myPoint2 result = s.trim().toUpperCase();
        
                //final String tokenSplit = "\t";
                final String tokenSplit = ",";
                String[] parts = s.split(tokenSplit);
                myPoint2 pt = new myPoint2(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        
                return pt;
            }
        });
        
        int xx = pointData.getNumPartitions();
        System.out.println("->> Num of partitions : " + xx);
        */

        class mbr implements Serializable {
            double maxX;
            double minX;
            double maxY;
            double minY;

            public mbr(double maxX, double minX, double maxY, double minY) {
                this.maxX = maxX;
                this.minX = minX;
                this.maxY = maxY;
                this.minY = minY;
            }
        }

        JavaRDD<mbr> pointData = inputFile.map(new Function<String, mbr>() {
            public mbr call(String s) {
                //myPoint2 result = s.trim().toUpperCase();

                //final String tokenSplit = "\t";
                final String tokenSplit = ",";
                String[] parts = s.split(tokenSplit);
                mbr pt = new mbr(Double.parseDouble(parts[1]), Double.POSITIVE_INFINITY, Double.parseDouble(parts[2]),
                        Double.POSITIVE_INFINITY);

                return pt;
            }
        });

        int xx = pointData.getNumPartitions();
        System.out.println("->> Num of partitions : " + xx);

        mbr myMbr = pointData.reduce(new Function2<mbr, mbr, mbr>() {
            public mbr call(mbr a, mbr b) {
                if (a.maxX < b.maxX) {
                    a.maxX = b.maxX;
                }
                if (a.minX > b.maxX) {
                    a.minX = b.maxX;
                }
                if (a.maxY < b.maxY) {
                    a.maxY = b.maxY;
                }
                if (a.minY > b.maxY) {
                    a.minY = b.maxY;
                }

                return a;
            }
        });

        System.out.println("selectivity = " + selectivity);

        double totalx = myMbr.maxX - myMbr.minX; // find the total area size, edge of square, etc
        double totaly = myMbr.maxY - myMbr.minY;
        double totalArea = totalx * totaly;
        double edge = Math.sqrt(selectivity * totalArea);

        System.out.println("minLongitude = " + myMbr.minX);
        System.out.println("maxLongitude = " + myMbr.maxX);
        System.out.println("minLatitude = " + myMbr.maxY);
        System.out.println("maxLatitude = " + myMbr.maxY);
        System.out.println("totalx = " + totalx);
        System.out.println("totaly = " + totaly);
        System.out.println("totalArea = " + totalArea);
        System.out.println("edge = " + edge);

        int mega = 1000000; // find the split
        //long numBin = (memoryBudget * mega) / 8;
        long numBin = 25;
        /**/
        double sqroot = Math.sqrt(numBin);
        final int printezis = (int) sqroot; // timh pou 8a ginetai to split
        double xSplit = totalx / printezis;
        final double ySplit = totaly / printezis;
        //List<Long> binCounter = new ArrayList<Long>() ;
        List<Long> binCounter = new ArrayList<Long>();
        long zero = 0;
        for (long i = 0; i < numBin; i++) {
            binCounter.add(zero);
        }
        int togoBin = 0;
        long tempcount1 = 0;

        //List<Double> xborders = new ArrayList<Double>();
        double[] xborders = new double[printezis + 1];
        double tempX;
        //xborders.add(minX.longitude); // create the xborders
        xborders[0] = myMbr.minX;
        for (int i = 1; i < printezis; i++) {
            tempX = i * xSplit + myMbr.minX;
            //xborders.add(tempX);
            xborders[i] = tempX;
        }
        //xborders.add(maxX.longitude);
        xborders[xborders.length - 1] = myMbr.maxX;

        for (int i = 0; i < printezis; i++) {

            final int temp = i;
            JavaRDD<myPoint2> horizLine = pointData.filter(new Function<myPoint2, Boolean>() {
                public Boolean call(myPoint2 p) throws Exception {
                    if ((p.latitude >= temp * ySplit) && (p.latitude < (temp + 1) * printezis)) {
                        //System.out.println("temp = " + temp);
                        return true;
                    } else {
                        return false;
                    }
                }
            });

            List<myPoint2> linePoints = new ArrayList<myPoint2>();

            linePoints = horizLine.collect();

            for (myPoint2 x : linePoints) {
                double tempbin = (x.longitude - minX.longitude) / xSplit;
                long xbin = (long) tempbin;

                togoBin = (int) ((i * printezis) + xbin);
                //System.out.println("---> " + togoBin);

                //System.out.print("---> " + binCounter.get(togoBin));
                tempcount1 = binCounter.get(togoBin);
                tempcount1++;
                binCounter.set(togoBin, tempcount1);
            }

        }

        for (int i = 0; i < binCounter.size(); i++) {
            System.out.println("-> " + i + " - " + binCounter.get(i));
        }

        // final count of points in each bin
        System.out.println("----------");
        int x = 0;
        int y = 0;
        int tempBinUp = 0;
        int tempBinLeft = 0;
        int tempBinDiag = 0;
        for (int i = 0; i < binCounter.size(); i++) {
            x = i % printezis;
            y = i / printezis;
            //System.out.println(x + " - " + y);
            if ((x == 0) && (y == 0)) {
                // Do nothing
            } else if (x == 0) {
                tempBinLeft = ((y - 1) * printezis) + x;
                tempcount1 = binCounter.get(i) + binCounter.get(tempBinLeft);
                binCounter.set(i, tempcount1);
            } else if (y == 0) {
                tempBinUp = (y * printezis) + (x - 1);
                tempcount1 = binCounter.get(i) + binCounter.get(tempBinUp);
                binCounter.set(i, tempcount1);
            } else {
                tempBinUp = (y * printezis) + (x - 1);
                tempBinLeft = ((y - 1) * printezis) + x;
                tempBinDiag = ((y - 1) * printezis) + (x - 1);
                tempcount1 = binCounter.get(i) + binCounter.get(tempBinUp) + binCounter.get(tempBinLeft)
                        - binCounter.get(tempBinDiag);
                binCounter.set(i, tempcount1);

            }
            //System.out.println(x + " - " + y + " - " + binCounter.get(i)) ;
        }

        for (int i = 0; i < binCounter.size(); i++) {
            System.out.println("-> " + i + " - " + binCounter.get(i));
        }
        /**/
        long endTime = System.nanoTime();
        long duration = (endTime - startTime); //divide by 1000000 to get milliseconds
        System.out.println("-----> Data preprocess time : " + (duration / 1000000000));

        /*
        for (int i = 0; i < queryPoints.length; i++) {
            startTime = System.nanoTime();
        
            answerQuery(i, maxLongitude, minLongitude, maxLatitude, minLatitude, printezis, xSplit, ySplit,
                    selectivity);
        
            endTime = System.nanoTime();
            duration = (endTime - startTime); //divide by 1000000 to get milliseconds
            System.out.println("Execution time query " + i + " : " + duration / 1000);
        }
        selectivity = 0.1;
        double realSelectivity = selectivity / 100;
        System.out.println("----------------------------------> selectivity = " + selectivity);
        for (int i = 0; i < queryPoints.length; i++) {
            startTime = System.nanoTime();
        
            answerQuery(i, maxLongitude, minLongitude, maxLatitude, minLatitude, printezis, xSplit, ySplit,
                    realSelectivity);
        
            endTime = System.nanoTime();
            duration = (endTime - startTime); //divide by 1000000 to get milliseconds
            System.out.println("Execution time query " + i + " : " + duration / 1000);
        }
        */
    }

}
