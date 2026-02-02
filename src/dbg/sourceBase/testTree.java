package dbg.sourceBase;

public class testTree {

        static class Node {
            String name;
            Node next;
            Node(String name) { this.name = name; }
        }


        public static void main(String[] args) {

            int[] numbers = {10, 20, 30, 40, 50};
            String[] fruits = {"Pomme", "Banane", "Cerise"};


            String message = "Hello Debugger";
            double pi = 3.14159;


            Node nodeA = new Node("A");
            Node nodeB = new Node("B");
            nodeA.next = nodeB;
            nodeB.next = nodeA;


            System.out.println("Pause ici pour inspecter les variables...");


            message = "Update successful";
            System.out.println("Fin du test.");
        }
    }

