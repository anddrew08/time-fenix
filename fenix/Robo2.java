package fenix;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.IOException;

// rei, o guarda do time, mantem distancia, desvia dos tiros e preve o movimento do inimigo
public class Rei extends TeamRobot {

    // direcao do movimento, 1 vai pra frente e -1 vai pra tras
    private int direcao = 1;

    // nome do inimigo que o time esta focando no momento
    private String alvo = null;

    // energia que o alvo tinha na ultima leitura, -1 significa que ainda nao sabemos
    private double energiaInimigo = -1;

    // distancia que o robo tenta manter do inimigo
    private static final double DISTANCIA_IDEAL = 300;

    // distancia minima das paredes antes de comecar a fugir delas
    private static final double MARGEM_PAREDE = 80;

    public void run() {
        // define as cores do corpo, do canhao e do radar
        setColors(Color.white, Color.blue, Color.cyan);

        // deixa canhao e radar girarem independentes do corpo
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);

        // laco principal, roda ate o fim da batalha
        while (true) {
            // se o radar parou, volta a girar sem parar procurando inimigos
            if (getRadarTurnRemaining() == 0) {
                setTurnRadarRight(Double.POSITIVE_INFINITY);
            }
            // executa todas as acoes pendentes neste turno
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // ignora o companheiro de time, nunca atira nele
        if (isTeammate(e.getName())) {
            return;
        }

        // escolhe um alvo se nao tem nenhum, ou troca se o inimigo esta muito perto
        if (alvo == null || e.getDistance() < 150) {
            if (!e.getName().equals(alvo)) {
                // alvo novo, esquece a energia antiga pra nao confundir com um tiro
                energiaInimigo = -1;
            }
            alvo = e.getName();
        }

        // se este inimigo nao e o alvo, ignora
        if (!e.getName().equals(alvo)) {
            return;
        }

        // avisa o companheiro de time qual e o alvo
        try {
            broadcastMessage(alvo);
        } catch (IOException ex) {
            // se falhar, segue o jogo sem avisar
        }

        // angulo absoluto do inimigo em relacao ao norte
        double anguloAbsoluto = getHeading() + e.getBearing();

        // trava o radar no inimigo, girando um pouco alem pra nao perder ele
        double giroRadar = Utils.normalRelativeAngleDegrees(anguloAbsoluto - getRadarHeading());
        setTurnRadarRight(giroRadar * 2);

        // quanto mais perto o inimigo, mais forte e o tiro
        double potencia = Math.min(3, Math.max(1, 400 / e.getDistance()));

        // com pouca energia, economiza atirando mais fraco
        if (getEnergy() < 20) {
            potencia = Math.min(potencia, 1.5);
        }

       
// velocidade da bala depende da potencia, quanto mais forte, mais lenta
double velocidadeBala = 20 - 3 * potencia;

// posicao atual do inimigo no campo
double inimigoX = getX() + e.getDistance()
        * Math.sin(Math.toRadians(anguloAbsoluto));
double inimigoY = getY() + e.getDistance()
        * Math.cos(Math.toRadians(anguloAbsoluto));


        // posicao futura estimada, comeca igual a atual
        double futuroX = inimigoX;
        double futuroY = inimigoY;
        double tempo = 0;

        // simula o inimigo andando em linha reta ate a bala conseguir alcanca-lo
        while ((++tempo) * velocidadeBala < Point2D.distance(getX(), getY(), futuroX, futuroY)) {
            futuroX += Math.sin(e.getHeadingRadians()) * e.getVelocity();
            futuroY += Math.cos(e.getHeadingRadians()) * e.getVelocity();

            // se a previsao saiu do campo, trava na parede e para de simular
            if (futuroX < 18 || futuroY < 18
                    || futuroX > getBattleFieldWidth() - 18
                    || futuroY > getBattleFieldHeight() - 18) {
                futuroX = Math.min(Math.max(18, futuroX), getBattleFieldWidth() - 18);
                futuroY = Math.min(Math.max(18, futuroY), getBattleFieldHeight() - 18);
                break;
            }
        }

        // angulo ate o ponto futuro, e quanto o canhao precisa girar pra chegar la
        double anguloMira = Math.toDegrees(Math.atan2(futuroX - getX(), futuroY - getY()));
        double giroCanhao = Utils.normalRelativeAngleDegrees(anguloMira - getGunHeading());
        setTurnGunRight(giroCanhao);

        // so atira se o canhao esfriou e esta bem alinhado
        if (getGunHeat() == 0 && Math.abs(giroCanhao) < 5) {
            setFire(potencia);
        }

        // se a energia do inimigo caiu entre 0,1 e 3, ele provavelmente atirou, entao desvia
        if (energiaInimigo >= 0) {
            double queda = energiaInimigo - e.getEnergy();
            if (queda > 0 && queda <= 3) {
                direcao = -direcao;
            }
        }
        energiaInimigo = e.getEnergy();

        // verifica se esta perto de alguma parede
        boolean pertoParede = getX() < MARGEM_PAREDE
                || getY() < MARGEM_PAREDE
                || getX() > getBattleFieldWidth() - MARGEM_PAREDE
                || getY() > getBattleFieldHeight() - MARGEM_PAREDE;

        if (pertoParede) {
            // perto da parede, vira em direcao ao centro do campo e segue em frente
            double anguloCentro = Math.toDegrees(Math.atan2(
                    getBattleFieldWidth() / 2 - getX(),
                    getBattleFieldHeight() / 2 - getY()));
            setTurnRight(Utils.normalRelativeAngleDegrees(anguloCentro - getHeading()));
            setAhead(100);
        } else {
            // longe das paredes, anda em circulos em volta do inimigo
            // se esta longe se aproxima, se esta perto se afasta
            double ajuste = e.getDistance() > DISTANCIA_IDEAL ? 25 : -25;
            setTurnRight(Utils.normalRelativeAngleDegrees(e.getBearing() + 90 - ajuste * direcao));
            setAhead(100 * direcao);
        }

        // de vez em quando inverte a direcao pra ficar dificil de acertar
        if (Math.random() < 0.03) {
            direcao = -direcao;
        }
    }

    public void onMessageReceived(MessageEvent e) {
        // recebe o alvo escolhido pelo companheiro e passa a focar nele
        if (e.getMessage() instanceof String) {
            String novoAlvo = (String) e.getMessage();
            if (!novoAlvo.equals(alvo)) {
                // trocou de alvo, esquece a energia antiga
                energiaInimigo = -1;
            }
            alvo = novoAlvo;
        }
    }

    public void onRobotDeath(RobotDeathEvent e) {
        // se o alvo morreu, libera pra escolher outro
        if (e.getName().equals(alvo)) {
            alvo = null;
            energiaInimigo = -1;
        }
    }

    public void onHitWall(HitWallEvent e) {
        // bateu na parede, volta na direcao contraria
        direcao = -direcao;
    }

    public void onHitRobot(HitRobotEvent e) {
        // bateu em alguem, recua
        direcao = -direcao;
    }

    public void onWin(WinEvent e) {
        // comemora a vitoria girando
        for (int i = 0; i < 30; i++) {
            turnRight(30);
            turnLeft(30);
        }
    }
}
