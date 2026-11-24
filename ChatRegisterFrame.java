package ChatProject;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/** GUI 회원가입: 중복검사는 DB에서 확인하고 최종 INSERT 직전에도 다시 확인한다. */
public class ChatRegisterFrame extends JFrame {
    private final JTextField id = new JTextField(20), nickname = new JTextField(20), zip=new JTextField(20);
    private final JTextField address=new JTextField(20), detail=new JTextField(20), question=new JTextField(20), answer=new JTextField(20);
    private final JPasswordField password=new JPasswordField(20), confirm=new JPasswordField(20);
    private final JLabel idStatus=new JLabel("아이디 중복 확인 필요"), nickStatus=new JLabel("닉네임 중복 확인 필요");
    private String checkedId=null, checkedNickname=null;
    public ChatRegisterFrame() {
        setTitle("ChatProject - 회원가입");setSize(510,700);setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel fields=new JPanel(new GridBagLayout());fields.setBorder(BorderFactory.createEmptyBorder(15,18,15,18));
        GridBagConstraints g=new GridBagConstraints();g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(5,5,5,5);g.weightx=1;
        int y=0;
        addField(fields,g,y++,"아이디",id,checkButton("아이디 중복 확인",true),idStatus);
        addField(fields,g,y++,"비밀번호 (8자 이상)",password,null,null);
        addField(fields,g,y++,"비밀번호 확인",confirm,null,null);
        addField(fields,g,y++,"닉네임",nickname,checkButton("닉네임 중복 확인",false),nickStatus);
        addField(fields,g,y++,"우편번호 (5자리)",zip,null,null);
        addField(fields,g,y++,"기본주소",address,null,null);
        addField(fields,g,y++,"상세주소",detail,null,null);
        addField(fields,g,y++,"보안질문",question,null,null);
        addField(fields,g,y++,"보안질문 답변",answer,null,null);
        JButton submit=new JButton("회원가입 완료");submit.addActionListener(e->submit());
        JPanel main=new JPanel(new BorderLayout());main.add(new JScrollPane(fields),BorderLayout.CENTER);
        main.add(submit,BorderLayout.SOUTH);setContentPane(main);setVisible(true);
    }
    private JButton checkButton(String label,boolean isId){
        JButton b=new JButton(label); b.addActionListener(e->{
            String value=(isId?id:nickname).getText().trim();
            if(isId && !value.matches("[A-Za-z0-9_]{3,30}")){show("아이디는 영문·숫자·밑줄 3~30자입니다.");return;}
            if(!isId && (value.isEmpty()||value.length()>30)){show("닉네임은 1~30자입니다.");return;}
            b.setEnabled(false);
            new SwingWorker<Boolean,Void>() {
                protected Boolean doInBackground() throws Exception {return isId?MemberGuiService.idExists(value):MemberGuiService.nicknameExists(value);}
                protected void done(){try{
                    boolean duplicate=get();if(isId){checkedId=duplicate?null:value;idStatus.setText(duplicate?"이미 사용 중":"사용 가능");}
                    else {checkedNickname=duplicate?null:value;nickStatus.setText(duplicate?"이미 사용 중":"사용 가능");}
                }catch(Exception ex){show("DB 중복검사 실패: "+ex.getMessage());}finally{b.setEnabled(true);}}
            }.execute();
        });return b;
    }
    private void addField(JPanel p,GridBagConstraints g,int y,String label,JTextField field,JButton check,JLabel status){
        g.gridy=y;g.gridx=0;g.weightx=0.32;p.add(new JLabel(label),g);
        JPanel right=new JPanel(new BorderLayout(4,2));right.add(field,BorderLayout.CENTER);
        if(check!=null)right.add(check,BorderLayout.EAST);
        if(status!=null)right.add(status,BorderLayout.SOUTH);
        g.gridx=1;g.weightx=0.68;p.add(right,g);
    }
    private void submit(){
        String userId=id.getText().trim(), nick=nickname.getText().trim();
        if(!userId.equals(checkedId)||!nick.equals(checkedNickname)){show("아이디와 닉네임 중복 확인을 완료해주세요.");return;}
        char[] pw=password.getPassword(),cpw=confirm.getPassword();
        if(!Arrays.equals(pw,cpw)){show("비밀번호 확인이 일치하지 않습니다.");Arrays.fill(pw,'\0');Arrays.fill(cpw,'\0');return;}
        String value=new String(pw);Arrays.fill(pw,'\0');Arrays.fill(cpw,'\0');
        new SwingWorker<Void,Void>() {
            protected Void doInBackground() throws Exception {
                MemberGuiService.register(userId,value,nick,zip.getText().trim(),address.getText().trim(),detail.getText().trim(),question.getText().trim(),answer.getText().trim());return null;
            }
            protected void done(){try{get();show("회원가입 성공! 로그인 화면에서 접속하세요.");dispose();}
            catch(Exception ex){show("회원가입 실패: "+rootMessage(ex));}}
        }.execute();
    }
    private static String rootMessage(Throwable e){while(e.getCause()!=null)e=e.getCause();return e.getMessage();}
    private void show(String msg){JOptionPane.showMessageDialog(this,msg);}
}
