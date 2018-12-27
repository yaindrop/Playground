import java.io.IOException;
import java.nio.file.*;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.*;

public class Annealing {
    static class Point implements Comparable<Point>{
        double x, y;
        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
        @Override
        public String toString() {
            return "(" + x +"," + y +")";
        }
        @Override
        public int compareTo(Point o) {
            return x > o.x ? 1 : -1;
        }
    }
    public static void main(String[] args) {
        int size = 200;
        double lower = -10, upper = 10, step = (upper -lower) / size;
        ArrayList<Point> list = new ArrayList<>(size * size);
        for (double x = lower; x < upper; x += step) for (double y = lower; y < upper; y += step) list.add(new Point(x, y));

        final BiFunction<Double, Double, Double> f = (x, y) ->
                Math.sin(x) - y;
                // Math.sin(x*x+y*y) - Math.cos(x*y);
                // Math.abs(Math.sin(x*x-y*y)) - Math.sin(x+y) - Math.cos(x*y);
                // Math.exp(Math.sin(x) + Math.cos(y)) - Math.sin(Math.exp(x+y));
                // Math.sin(Math.sin(x) + Math.cos(y)) - Math.cos(Math.sin(x*y) + Math.cos(x));
                // Math.cos(Math.cos(Math.min(y+Math.sin(x), x+Math.sin(y)))) - Math.cos(Math.sin(Math.max(x+Math.sin(y), y+Math.sin(x))));
        final Function<Point, Double> potential = p -> Math.abs(f.apply(p.x, p.y));
        double Kb = 1.38064852E-23;
        final BiFunction<Double, Double, Double> metropolis = (delE, T) -> Math.exp(-delE/(T*Kb));

        double T = 5000;
        while (T > 0) {
            for (Point p : list) {
                Point rand = new Point(lower + Math.random() * (upper - lower), lower + Math.random() * (upper - lower));
                double delE = potential.apply(rand) - potential.apply(p);
                if (Math.random() < metropolis.apply(delE, T)) {
                    p.x = rand.x;
                    p.y = rand.y;
                }
            }
            T -= 10;
            System.out.println(T);
        }

        Collections.sort(list);

        StringBuilder xStr = new StringBuilder();
        StringBuilder yStr = new StringBuilder();
        xStr.append("x = [");
        yStr.append("y = [");
        for (int i = 0; i < list.size(); i ++) {
            Point p = list.get(i);
            xStr.append(p.x);
            yStr.append(p.y);
            if (i != list.size() - 1) {
                xStr.append(',');
                yStr.append(',');
            }
        }
        xStr.append(']');
        yStr.append(']');
        List<String> strs = new ArrayList<>();
        strs.add(xStr.toString());
        strs.add(yStr.toString());
        Path out = Paths.get("out.txt");

        try {
            Files.write(out, strs, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // scatter(x, y, 1) IN MATLAB
    }
}