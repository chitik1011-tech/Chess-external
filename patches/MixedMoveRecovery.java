package com.opencheps.vision;

/**
 * Reconstruct at most two alternating normal chess moves, including captures.
 * No guessing: the candidate board must EXACTLY equal the observed board,
 * every source/destination must obey piece geometry, and the mover's king
 * may not be in check. Castling, en passant, and promotion are excluded.
 *
 * Screen-order grids use uppercase white pieces and lowercase black pieces.
 */
public final class MixedMoveRecovery {
    private MixedMoveRecovery() {}

    public static int matchingPlies(char[][] before, char[][] observed,
                                    boolean whiteToMove, boolean whiteBottom) {
        if (!shapeOk(before) || !shapeOk(observed)) return 0;
        if (equal(before, observed)) return 0;
        if (!piecesOk(before) || !piecesOk(observed)) return 0;
        // Check a single move first. Two-ply replay is a fallback when
        // screen sampling missed the intermediate move.
        if (replayExact(before, observed, whiteToMove, whiteBottom, 1)) return 1;
        if (replayExact(before, observed, whiteToMove, whiteBottom, 2)) return 2;
        return 0;
    }

    private static boolean replayExact(char[][] before, char[][] target,
                                       boolean whiteToMove, boolean whiteBottom,
                                       int plies) {
        for (int sr = 0; sr < 8; sr++) for (int sc = 0; sc < 8; sc++) {
            char moving = before[sr][sc];
            if (moving == '.' || moving == 0
                    || Character.isUpperCase(moving) != whiteToMove
                    || target[sr][sc] == moving) continue;
            for (int tr = 0; tr < 8; tr++) for (int tc = 0; tc < 8; tc++) {
                // Require the moving piece to survive at its final square.
                // Reject transient recaptures and promotions rather than guessing.
                if (target[tr][tc] != moving || before[tr][tc] == moving) continue;
                char[][] mid = copy(before);
                if (!apply(mid,sr,sc,tr,tc,whiteToMove,whiteBottom)) continue;
                if (plies == 1) {
                    if (equal(mid,target)) return true;
                    continue;
                }
                if (replaySecond(mid,target,!whiteToMove,whiteBottom)) return true;
            }
        }
        return false;
    }

    private static boolean replaySecond(char[][] mid, char[][] target,
                                        boolean moverWhite, boolean whiteBottom) {
        for(int sr=0;sr<8;sr++)for(int sc=0;sc<8;sc++) {
            char piece = mid[sr][sc];
            if (piece=='.'||piece==0||Character.isUpperCase(piece)!=moverWhite
                    ||target[sr][sc]==piece) continue;
            for(int tr=0;tr<8;tr++)for(int tc=0;tc<8;tc++) {
                if(target[tr][tc]!=piece ||mid[tr][tc]==piece) continue;
                char[][] fin=copy(mid);
                if(apply(fin,sr,sc,tr,tc,moverWhite,whiteBottom)
                        &&equal(fin,target)) return true;
            }
        }
        return false;
    }

    private static boolean apply(char[][] b,int sr,int sc,int tr,int tc,
                                 boolean white, boolean whiteBottom) {
        if(sr==tr&&sc==tc)return false;
        char p=b[sr][sc], captured=b[tr][tc];
        if(p=='.'||p==0||Character.isUpperCase(p)!=white
                ||(captured!='.'&&captured!=0
                    &&(Character.isUpperCase(captured)==white
                        ||Character.toLowerCase(captured)=='k')))return false;
        int dr=tr-sr,dc=tc-sc,ar=Math.abs(dr),ac=Math.abs(dc);
        boolean capture=captured!='.'&&captured!=0;
        boolean allowed;
        switch(Character.toLowerCase(p)){
            case 'p':
                int step=(white==whiteBottom)?-1:1;
                int home=(step==-1)?6:1;
                allowed = capture
                    ? dr==step&&ac==1
                    : dc==0&&(dr==step
                        || (dr==2*step &&sr==home&&b[sr+step][sc]=='.'));
                if(tr==0||tr==7)allowed=false; // promotion not handled
                break;
            case 'n': allowed=(ar==1&&ac==2)||(ar==2&&ac==1);break;
            case 'b': allowed=ar>0&&ar==ac&&clear(b,sr,sc,tr,tc);break;
            case 'r': allowed=((dr==0)!=(dc==0))&&clear(b,sr,sc,tr,tc);break;
            case 'q': allowed=((ar>0&&ar==ac)||((dr==0)!=(dc==0)))
                    &&clear(b,sr,sc,tr,tc);break;
            case 'k': allowed=Math.max(ar,ac)==1;break;
            default: return false;
        }
        if(!allowed)return false;
        b[tr][tc]=p;b[sr][sc]='.';
        return !isKingAttacked(b,white,whiteBottom);
    }

    private static boolean clear(char[][] b,int sr,int sc,int tr,int tc){
        int rd=Integer.compare(tr,sr),cd=Integer.compare(tc,sc);
        int r=sr+rd,c=sc+cd;
        while(r!=tr||c!=tc){
            if(b[r][c]!='.')return false;
            r+=rd;c+=cd;
        }
        return true;
    }

    private static boolean isKingAttacked(char[][] b,boolean white,boolean whiteBottom){
        int kr=-1,kc=-1;
        char king=white?'K':'k';
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)
            if(b[r][c]==king){kr=r;kc=c;}
        if(kr<0)return true;
        for(int r=0;r<8;r++)for(int c=0;c<8;c++){
            char p=b[r][c];
            if(p=='.'||p==0||Character.isUpperCase(p)==white)continue;
            int dr=kr-r,dc=kc-c,ar=Math.abs(dr),ac=Math.abs(dc);
            switch(Character.toLowerCase(p)){
                case 'p':
                    int step=((!white)==whiteBottom)?-1:1;
                    if(dr==step&&ac==1)return true;
                    break;
                case 'n':
                    if((ar==1&&ac==2)||(ar==2&&ac==1))return true;
                    break;
                case 'b':
                    if(ar>0&&ar==ac&&clear(b,r,c,kr,kc))return true;
                    break;
                case 'r':
                    if((dr==0)!=(dc==0) && clear(b,r,c,kr,kc))return true;
                    break;
                case 'q':
                    if(((ar>0&&ar==ac)||((dr==0)!=(dc==0)))
                        &&clear(b,r,c,kr,kc))return true;
                    break;
                case 'k':
                    if(Math.max(ar,ac)==1)return true;
                    break;
                default:break;
            }
        }
        return false;
    }

    private static boolean shapeOk(char[][] b){
        if(b==null||b.length!=8)return false;
        for(char[] row:b)if(row==null||row.length!=8)return false;
        return true;
    }

    private static boolean piecesOk(char[][] b){
        int wk=0,bk=0;
        for(char[] row:b)for(char p:row){
            if("prnbqkPRNBQK.".indexOf(p)<0)return false;
            if(p=='K')wk++;
            if(p=='k')bk++;
        }
        return wk==1&&bk==1;
    }
    private static char[][] copy(char[][] b){
        char[][] c=new char[8][8];
        for(int i=0;i<8;i++)c[i]=b[i].clone();
        return c;
    }
    private static boolean equal(char[][] a,char[][] b){
        for(int r=0;r<8;r++)for(int c=0;c<8;c++)
            if(a[r][c]!=b[r][c])return false;
        return true;
    }
}
