package fenix;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.io.IOException;

// asuka, a guarda agressiva do time, chega mais perto e atira mais forte
public class Asuka extends TeamRobot {

    // direcao do movimento, 1 vai pra frente e -1 vai pra tras
    private int direcao = 1;

    // nome do inimigo que o time esta focando no momento
    private String alvo = null;

    // distancia que o robo tenta manter do inimigo, menor que a do parceiro
    private static final double DISTANCIA_IDEAL = 180;

    public void run() {
        // define as cores do corpo, do canhao e do radar
        setColors(Color.red, Color.orange, Color.yellow);

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
        if (alvo == null || e.getDistance() < 120) {
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

        // aponta o canhao pro inimigo
        double giroCanhao = Utils.normalRelativeAngleDegrees(anguloAbsoluto - getGunHeading());
        setTurnGunRight(giroCanhao);

        // asuka atira mais forte, usa potencia maxima quando o inimigo esta perto
        double potencia = e.getDistance() < 250 ? 3 : 2;

        // so atira se o canhao esfriou e esta quase mirando
        if (getGunHeat() == 0 && Math.abs(giroCanhao) < 10) {
            setFire(potencia);
        }

        // anda em volta do inimigo, mas com curvas mais fechadas pra ficar mais perto
        double ajuste = e.getDistance() > DISTANCIA_IDEAL ? 35 : -15;
        setTurnRight(Utils.normalRelativeAngleDegrees(e.getBearing() + 90 - ajuste * direcao));
        setAhead(120 * direcao);

        // de vez em quando inverte a direcao pra ficar dificil de acertar
        if (Math.random() < 0.05) {
            direcao = -direcao;
        }
    }

    public void onMessageReceived(MessageEvent e) {
        // recebe o alvo escolhido pelo companheiro e passa a focar nele
        if (e.getMessage() instanceof String) {
            alvo = (String) e.getMessage();
        }
    }

    public void onRobotDeath(RobotDeathEvent e) {
        // se o alvo morreu, libera pra escolher outro
        if (e.getName().equals(alvo)) {
            alvo = null;
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

    public void onHitByBullet(HitByBulletEvent e) {
        // levou tiro, muda de direcao pra desviar do proximo
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
