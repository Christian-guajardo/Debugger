package dbg.sourceBase;

/**
 * Programme de test pour le Time-Traveling Debugger
 * Ce programme illustre toutes les fonctionnalités TTQ :
 * - Modifications de variables
 * - Appels de méthodes multiples
 * - Call stack variée
 * - Structures de données complexes
 */
public class testTree {

    static class Point {
        int x;
        int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        void move(int dx, int dy) {
            x += dx;  // Modification de x
            y += dy;  // Modification de y
        }

        double distanceFromOrigin() {
            return Math.sqrt(x * x + y * y);
        }
    }

    public static void main(String[] args) {
        System.out.println("=== TTQ Test Program ===");

        // Test 1: Modifications simples de variables
        int counter = 0;        // Initialisation
        System.out.println("Counter: " + counter);

        counter = 10;           // Modification 1
        System.out.println("Counter: " + counter);
        compute3(1);
        counter = 20;           // Modification 2
        System.out.println("Counter: " + counter);

        counter = processValue(counter);  // Modification 3
        System.out.println("Counter: " + counter);

        // Test 2: Appels de méthodes répétés
        int result1 = compute(5);
        int result2 = compute(10);
        int result3 = compute(15);

        System.out.println("Results: " + result1 + ", " + result2 + ", " + result3);

        // Test 3: Modifications dans des objets
        Point p = new Point(0, 0);
        System.out.println("Point créé");

        p.move(5, 3);           // Modifications de p.x et p.y
        System.out.println("Point moved");

        p.move(2, 7);           // Nouvelles modifications
        System.out.println("Point moved again");

        double distance = p.distanceFromOrigin();
        System.out.println("Distance: " + distance);

        // Test 4: Boucle avec modifications
        String message = "Start";  // Variable à suivre
        for (int i = 0; i < 3; i++) {
            message = updateMessage(message, i);  // Modifications multiples
            System.out.println("Message: " + message);
        }

        // Test 5: Appels imbriqués
        int finalValue = nestedCall1(100);
        System.out.println("Final value: " + finalValue);

        System.out.println("=== Test Complete ===");
    }

    public static int processValue(int value) {
        value = value * 2;      // Modification
        value = value + 5;      // Modification
        return value;
    }
    public static void compute3(int n) {
        int temp = n * 2;       // Appel répété 3 fois
        int bob =2;
        return;
    }
    public static int compute(int n) {
        int temp = n * 2;       // Appel répété 3 fois
        return helper(temp);
    }

    public static int helper(int x) {
        return x + 1;
    }

    public static String updateMessage(String msg, int index) {
        return msg + "-" + index;  // Modifications successives
    }

    public static int nestedCall1(int val) {
        System.out.println("Nested call 1");
        return nestedCall2(val - 10);
    }

    public static int nestedCall2(int val) {
        System.out.println("Nested call 2");
        return nestedCall3(val - 10);
    }

    public static int nestedCall3(int val) {
        System.out.println("Nested call 3");
        return val * 2;
    }
}