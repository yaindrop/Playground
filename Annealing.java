package Playground;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.*;

/*
 * Simple mathematical function graph drawer using annealing algorithm
 */

public class Annealing {
    interface QuadFunction<A, B, C, D, R> {R apply(A a, B b, C c, D d);}
    static class Point {
        double x, y;
        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
    public static void main(String[] args) {
        int size = 100;
        double xLower = -10, xUpper = 10, xStep = (xUpper -xLower) / size;
        double yLower = -10, yUpper = 10, yStep = (yUpper -yLower) / size;

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
        final BiFunction<Double, Double, Double> random = (l, u) -> l + Math.random() * (u - l);
        final QuadFunction<Double, Double, Double, Double, Double> restrictedRandom = (x, r, xu, xl) ->
            random.apply(Math.max(xl, x - r), Math.min(xu, x + r));

        List<Point> points = new ArrayList<>(size * size);
        for (double x = xLower; x < xUpper; x += xStep) for (double y = yLower; y < yUpper; y += yStep) points.add(new Point(x, y));

        double startT = 5000, endT = 1, decayRate = 0.999;
        for (double T = startT; T > endT; T *= decayRate) {
            for (Point p : points) {
                Point next = new Point(
                        restrictedRandom.apply(p.x, (xUpper - xLower) * Math.log(T) / Math.log(startT), xUpper, xLower),
                        restrictedRandom.apply(p.y, (yUpper - yLower) * Math.log(T) / Math.log(startT), yUpper, yLower)
                );
                double delE = potential.apply(next) - potential.apply(p);
                if (Math.random() < metropolis.apply(delE, T)) {
                    p.x = next.x;
                    p.y = next.y;
                }
            }
            System.out.println(T);
        }

        StringBuilder xStr = new StringBuilder();
        StringBuilder yStr = new StringBuilder();
        xStr.append("x = [");
        yStr.append("y = [");
        for (int i = 0; i < points.size(); i ++) {
            Point p = points.get(i);
            xStr.append(p.x);
            yStr.append(p.y);
            if (i != points.size() - 1) {
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
