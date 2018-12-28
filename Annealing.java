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
    interface FourthFunction<A, B, C, D, R> {R apply(A a, B b, C c, D d);}
    interface FifthFunction<A, B, C, D, E, R> {R apply(A a, B b, C c, D d, E e);}

    static class Point {
        double x, y;
        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
    public static void main(String[] args) {
        final int size = 50;
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

        List<Point> points = new ArrayList<>(size * size);
        for (double x = xLower; x < xUpper; x += xStep) for (double y = yLower; y < yUpper; y += yStep) points.add(new Point(x, y));

        // Decay -> Quality: 0.99 -> low, 0.999 -> Med, 0.9999 -> High
        annealing(points, potential, 1000, 1, 0.999, new Point(xLower, yLower), new Point(xUpper, yUpper));

        StringBuilder xStr = new StringBuilder();
        StringBuilder yStr = new StringBuilder();
        xStr.append("x = [");
        yStr.append("y = [");
        points.forEach(p -> {
            xStr.append(p.x).append(',');
            yStr.append(p.y).append(',');
        });
        xStr.replace(xStr.length() - 1, xStr.length(), "]");
        yStr.replace(yStr.length() - 1, yStr.length(), "]");

        List<String> lines = new ArrayList<>();
        lines.add(xStr.toString());
        lines.add(yStr.toString());
        Path out = Paths.get("out.txt");
        try {
            Files.write(out, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // scatter(x, y, 1) IN MATLAB
    }

    static void annealing(List<Point> points, Function<Point, Double> potential, double startT, double endT, double decay, Point leftLower, Point upperHigher) {
        double xLower = leftLower.x, xUpper = upperHigher.x, yLower = leftLower.y, yUpper = upperHigher.y;
        final double Kb = 1.38064852E-23;
        final BiFunction<Double, Double, Double> metropolis = (delE, T) -> Math.exp(-delE/(T*Kb));
        final BiFunction<Double, Double, Double> random = (l, u) -> l + Math.random() * (u - l);
        final FourthFunction<Double, Double, Double, Double, Double> restrictedRandom = (x, r, xl, xu) ->
                random.apply(Math.max(xl, x - r), Math.min(xu, x + r));
        final FifthFunction<Double, Double, Double, Double, Double, Double> restriction = (t, st, et, l, u) ->
                (u - l) * Math.log(t/et) / Math.log(st/et);
        for (double T = startT; T > endT; T *= decay) {
            for (Point p : points) {
                Point next = new Point(
                        restrictedRandom.apply(p.x, restriction.apply(T, startT, endT, xLower, xUpper), xLower, xUpper),
                        restrictedRandom.apply(p.y, restriction.apply(T, startT, endT, yLower, yUpper), yLower, yUpper)
                );
                double delE = potential.apply(next) - potential.apply(p);
                if (Math.random() < metropolis.apply(delE, T)) {
                    p.x = next.x;
                    p.y = next.y;
                }
            }
            System.out.println(T);
        }
    }
}
