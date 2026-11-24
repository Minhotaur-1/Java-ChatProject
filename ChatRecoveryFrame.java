package ChatProject;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/** 아이디 찾기 / 비밀번호 재설정 - 7주차 보안질문 해시 검사 재사용. */
public class ChatRecoveryFrame extends JFrame {
    private final JTextField identifier=new JTextField(22), answer=new JTextField(22);
    private final JPasswordField newPassword=new JPasswordField(22), confirmation=new JPasswordField(22);
    private final JLabel questionLabel=new JLabel("보안질문 조회 버튼을 눌러주세요.");
    private final boolean findId;
    public ChatRecoveryFrame(boolean findId){
        this.findId=findId;
        setTitle(findId?"ChatProject - 아이디 찾기":"ChatProject - 비밀번호 재설정");
        setSize(450,findId?340:430);setLocationRelativeTo(null);setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel p=new JPanel(new GridLayout(0,1,5,5));p.setBorder(BorderFactory.createEmptyBorder(18,25,18,25));
        p.add(new JLabel(findId?"가입한 닉네임":"회원 아이디"));p.add(identifier);
        JButton load=new JButton("보안질문 확인");p.add(load);p.add(questionLabel);
        p.add(new JLabel("보안질문 답변"));p.add(answer);
        if(!findId){p.add(new JLabel("새 비밀번호 (8~100자)"));p.add(newPassword);p.add(new JLabel("새 비밀번호 확인"));p.add(confirmation);}
        JButton submit=new JButton(findId?"아이디 찾기":"비밀번호 재설정");p.add(submit);
        load.addActionListener(e->{String v=identifier.getText().trim();if(v.isEmpty())return;
            new SwingWorker<String,Void>(){
                protected String doInBackground()throws Exception{return findId?MemberGuiService.getQuestionByNickname(v):MemberGuiService.getQuestionById(v);}
                protected void done(){try{String q=get();questionLabel.setText(q==null?"등록된 질문/회원 없음":q);}catch(Exception ex){show(ex.getMessage());}}
            }.execute();
        });
        submit.addActionListener(e->submit());add(p);setVisible(true);
    }
    private void submit(){String v=identifier.getText().trim(),a=answer.getText();
        char[] pw=newPassword.getPassword(),cpw=confirmation.getPassword();
        if(!findId&&!Arrays.equals(pw,cpw)){show("새 비밀번호가 일치하지 않습니다.");return;}
        String newPw=new String(pw);Arrays.fill(pw,'\0');Arrays.fill(cpw,'\0');
        new SwingWorker<String,Void>() {
            protected String doInBackground()throws Exception {
                if(findId)return "회원 아이디: "+MemberGuiService.findId(v,a);
                MemberGuiService.resetPassword(v,a,newPw);return "비밀번호 재설정 완료. 다시 로그인해주세요.";
            }
            protected void done(){try{show(get());dispose();}catch(Exception ex){Throwable t=ex;while(t.getCause()!=null)t=t.getCause();show("처리 실패: "+t.getMessage());}}
        }.execute();
    }
    private void show(String s){JOptionPane.showMessageDialog(this,s);}
}
