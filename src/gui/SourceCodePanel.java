package gui;


import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class SourceCodePanel extends JPanel {
    private List<String> sourceLines;
    private int currentLine = -1;
    private Set<Integer> breakpoints;
    private BreakpointClickListener breakpointListener;

    // Constantes de style
    private static final Color BACKGROUND_COLOR = new Color(43, 43, 43);
    private static final Color LINE_NUMBER_BG = new Color(60, 60, 60);
    private static final Color LINE_NUMBER_FG = new Color(128, 128, 128);
    private static final Color CODE_FG = new Color(220, 220, 220);
    private static final Color CURRENT_LINE_BG = new Color(255, 255, 0, 80);
    private static final Color CURRENT_LINE_BORDER = new Color(255, 200, 0);
    private static final Color BREAKPOINT_COLOR = new Color(220, 50, 50);
    private static final Color BREAKPOINT_BORDER = new Color(180, 30, 30);

    private static final int LINE_HEIGHT = 20;
    private static final int LINE_NUMBER_WIDTH = 50;
    private static final int MARGIN = 5;

    private JScrollPane scrollPane;
    private CodeDisplayPanel codePanel;

    public interface BreakpointClickListener {
        void onBreakpointToggle(int lineNumber);
    }

    public SourceCodePanel() {
        sourceLines = new ArrayList<>();
        breakpoints = new HashSet<>();

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Source Code"));

        codePanel = new CodeDisplayPanel();
        scrollPane = new JScrollPane(codePanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        add(scrollPane, BorderLayout.CENTER);
    }

    public void setSourceLines(List<String> lines) {
        this.sourceLines = new ArrayList<>(lines);
        codePanel.updateSize();
        codePanel.repaint();
    }

    public void setCurrentLine(int line) {
        this.currentLine = line;
        codePanel.repaint();

        // Scroll vers la ligne courante
        if (line > 0 && line <= sourceLines.size()) {
            Rectangle rect = new Rectangle(0, (line - 1) * LINE_HEIGHT - 100,
                    codePanel.getWidth(), LINE_HEIGHT + 200);
            codePanel.scrollRectToVisible(rect);
        }
    }

    public void addBreakpoint(int line) {
        breakpoints.add(line);
        codePanel.repaint();
    }

    public void removeBreakpoint(int line) {
        breakpoints.remove(line);
        codePanel.repaint();
    }

    public void toggleBreakpoint(int line) {
        if (breakpoints.contains(line)) {
            removeBreakpoint(line);
        } else {
            addBreakpoint(line);
        }
    }

    public void clearBreakpoints() {
        breakpoints.clear();
        codePanel.repaint();
    }

    public void setBreakpointListener(BreakpointClickListener listener) {
        this.breakpointListener = listener;
    }

    // ========================================================================
    // Panel interne pour afficher le code
    // ========================================================================

    private class CodeDisplayPanel extends JPanel {

        public CodeDisplayPanel() {
            setBackground(BACKGROUND_COLOR);

            // Gestion des clics pour les breakpoints
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    handleClick(e);
                }
            });

            // Menu contextuel
            setComponentPopupMenu(createPopupMenu());
        }

        private void handleClick(MouseEvent e) {
            int clickedLine = (e.getY() / LINE_HEIGHT) + 1;

            if (clickedLine > 0 && clickedLine <= sourceLines.size()) {
                // Clic dans la zone des numéros de ligne
                if (e.getX() <= LINE_NUMBER_WIDTH) {
                    toggleBreakpoint(clickedLine);

                    if (breakpointListener != null) {
                        breakpointListener.onBreakpointToggle(clickedLine);
                    }
                }
            }
        }

        private JPopupMenu createPopupMenu() {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem toggleBP = new JMenuItem("Toggle Breakpoint");
            toggleBP.addActionListener(e -> {
                Point mousePos = getMousePosition();
                if (mousePos != null) {
                    int line = (mousePos.y / LINE_HEIGHT) + 1;
                    if (line > 0 && line <= sourceLines.size()) {
                        toggleBreakpoint(line);
                        if (breakpointListener != null) {
                            breakpointListener.onBreakpointToggle(line);
                        }
                    }
                }
            });
            menu.add(toggleBP);

            return menu;
        }

        public void updateSize() {
            int height = sourceLines.size() * LINE_HEIGHT + MARGIN * 2;
            int width = 800; // Largeur minimale
            setPreferredSize(new Dimension(width, height));
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            // Anti-aliasing pour un rendu plus lisse
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            if (sourceLines.isEmpty()) {
                drawEmptyMessage(g2);
                return;
            }

            Font font = new Font("Consolas", Font.PLAIN, 13);
            if (font.getFamily().equals("Dialog")) {
                font = new Font("Monospaced", Font.PLAIN, 13);
            }
            g2.setFont(font);

            FontMetrics fm = g2.getFontMetrics();
            int textOffset = fm.getAscent();

            for (int i = 0; i < sourceLines.size(); i++) {
                int lineNumber = i + 1;
                int y = i * LINE_HEIGHT;

                // Dessiner la ligne courante en surbrillance
                if (lineNumber == currentLine) {
                    drawCurrentLine(g2, y);
                }

                // Dessiner la zone des numéros de ligne
                drawLineNumberArea(g2, lineNumber, y);

                // Dessiner le breakpoint si présent
                if (breakpoints.contains(lineNumber)) {
                    drawBreakpoint(g2, y);
                }

                // Dessiner le code
                drawCodeLine(g2, sourceLines.get(i), y, textOffset, font);
            }
        }

        private void drawEmptyMessage(Graphics2D g2) {
            g2.setColor(LINE_NUMBER_FG);
            g2.setFont(new Font("SansSerif", Font.ITALIC, 14));
            String msg = "No source code available";
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(msg)) / 2;
            int y = getHeight() / 2;
            g2.drawString(msg, x, y);
        }

        private void drawCurrentLine(Graphics2D g2, int y) {
            // Fond de la ligne courante
            g2.setColor(CURRENT_LINE_BG);
            g2.fillRect(0, y, getWidth(), LINE_HEIGHT);

            // Bordure gauche de la ligne courante
            g2.setColor(CURRENT_LINE_BORDER);
            g2.fillRect(0, y, 3, LINE_HEIGHT);
        }

        private void drawLineNumberArea(Graphics2D g2, int lineNumber, int y) {
            // Fond de la zone des numéros
            g2.setColor(LINE_NUMBER_BG);
            g2.fillRect(0, y, LINE_NUMBER_WIDTH, LINE_HEIGHT);

            // Séparateur vertical
            g2.setColor(new Color(80, 80, 80));
            g2.drawLine(LINE_NUMBER_WIDTH, y, LINE_NUMBER_WIDTH, y + LINE_HEIGHT);

            // Numéro de ligne
            g2.setColor(LINE_NUMBER_FG);
            String lineNum = String.valueOf(lineNumber);
            FontMetrics fm = g2.getFontMetrics();
            int numWidth = fm.stringWidth(lineNum);
            int textX = LINE_NUMBER_WIDTH - numWidth - 10;
            int textY = y + (LINE_HEIGHT + fm.getAscent()) / 2 - 2;
            g2.drawString(lineNum, textX, textY);
        }

        private void drawBreakpoint(Graphics2D g2, int y) {
            int size = 12;
            int x = 8;
            int yCenter = y + LINE_HEIGHT / 2;

            // Cercle rouge pour le breakpoint
            g2.setColor(BREAKPOINT_COLOR);
            g2.fillOval(x - size/2, yCenter - size/2, size, size);

            // Bordure
            g2.setColor(BREAKPOINT_BORDER);
            g2.setStroke(new BasicStroke(2));
            g2.drawOval(x - size/2, yCenter - size/2, size, size);
            g2.setStroke(new BasicStroke(1));
        }

        private void drawCodeLine(Graphics2D g2, String line, int y, int textOffset, Font font) {
            g2.setColor(CODE_FG);
            g2.setFont(font);

            int textX = LINE_NUMBER_WIDTH + 10;
            int textY = y + (LINE_HEIGHT + textOffset) / 2 - 2;

            // Colorisation syntaxique basique
            drawSyntaxHighlightedLine(g2, line, textX, textY);
        }

        private void drawSyntaxHighlightedLine(Graphics2D g2, String line, int x, int y) {
            // Couleurs pour la colorisation syntaxique
            Color keywordColor = new Color(204, 120, 50);
            Color stringColor = new Color(106, 135, 89);
            Color commentColor = new Color(128, 128, 128);
            Color numberColor = new Color(104, 151, 187);

            String[] keywords = {"public", "private", "static", "void", "class", "int",
                    "double", "String", "return", "if", "else", "for", "while",
                    "new", "this", "super", "import", "package"};

            // Détection de commentaires
            if (line.trim().startsWith("//")) {
                g2.setColor(commentColor);
                g2.drawString(line, x, y);
                return;
            }

            // Colorisation simple mot par mot
            FontMetrics fm = g2.getFontMetrics();
            int currentX = x;

            String[] tokens = line.split("(?<=\\W)|(?=\\W)");

            for (String token : tokens) {
                Color color = CODE_FG;

                // Keywords
                for (String keyword : keywords) {
                    if (token.equals(keyword)) {
                        color = keywordColor;
                        break;
                    }
                }

                // Strings
                if (token.startsWith("\"") || token.startsWith("'")) {
                    color = stringColor;
                }

                // Numbers
                if (token.matches("\\d+")) {
                    color = numberColor;
                }

                g2.setColor(color);
                g2.drawString(token, currentX, y);
                currentX += fm.stringWidth(token);
            }
        }
    }
}