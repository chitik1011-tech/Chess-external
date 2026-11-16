#include <jni.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <string>

#include "imgui.h"
#include "backends/imgui_impl_opengl3.h"

namespace {
struct BoardState {
    std::string move, mode = "arrow", event;
    int quality = 100;
    int speed = 100;
    bool flipped = false;
    long long eventId = 0;
    float width = 0.f, height = 0.f;
};
BoardState g;
bool gReady = false;
double gFxStart = -1.0;
double gLastFrame = 0.0;

double seconds() {
    using clock = std::chrono::steady_clock;
    return std::chrono::duration<double>(clock::now().time_since_epoch()).count();
}
std::string fromJni(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* utf = env->GetStringUTFChars(s, nullptr);
    if (!utf) return {};
    std::string value(utf);
    env->ReleaseStringUTFChars(s, utf);
    return value;
}
ImU32 rgba(int r,int g,int b,int a=255) { return IM_COL32(r,g,b,a); }
ImVec2 cell(const std::string& sq) {
    if (sq.size()<2) return ImVec2(-100.f,-100.f);
    int file = sq[0]-'a', rank = sq[1]-'1';
    int x=g.flipped?7-file:file;
    int y=g.flipped?rank:7-rank;
    return ImVec2((x+.5f)*g.width/8.f,(y+.5f)*g.height/8.f);
}
void roundedGlow(ImDrawList* dl,ImVec2 a,ImVec2 b,ImU32 color,float thickness) {
    for (int i=5;i>=1;i--) {
        ImU32 c=(color & 0x00ffffffu) | ((ImU32)(8+(5-i)*5)<<24);
        dl->AddRect(ImVec2(a.x-i,a.y-i),ImVec2(b.x+i,b.y+i),c,9.f,0,thickness+i*1.3f);
    }
    dl->AddRect(a,b,color,8.f,0,thickness);
}
void textBadge(ImDrawList* dl,ImVec2 anchor,const std::string& label,ImU32 border) {
    ImVec2 sz=ImGui::CalcTextSize(label.c_str());
    float x=std::clamp(anchor.x,sz.x*.5f+11,g.width-sz.x*.5f-11);
    float y=std::max(sz.y+8.f,anchor.y-g.width/8.f*.62f);
    ImVec2 lo(x-sz.x*.5f-11,y-sz.y-5),hi(x+sz.x*.5f+11,y+6);
    dl->AddRectFilled(lo,hi,rgba(7,26,29,235),9.f);
    dl->AddRect(lo,hi,border,9.f,0,1.6f);
    dl->AddText(ImVec2(x-sz.x*.5f,y-sz.y),rgba(245,255,251),label.c_str());
}
void drawCrown(ImDrawList* dl,ImVec2 c,float size,ImU32 color) {
    // A vector king crown, no emoji font needed.
    ImVec2 p[]={
        ImVec2(c.x-size*.48f,c.y+size*.30f),
        ImVec2(c.x-size*.50f,c.y-size*.20f),
        ImVec2(c.x-size*.24f,c.y-size*.05f),
        ImVec2(c.x,c.y-size*.48f),
        ImVec2(c.x+size*.24f,c.y-size*.05f),
        ImVec2(c.x+size*.50f,c.y-size*.20f),
        ImVec2(c.x+size*.48f,c.y+size*.30f)
    };
    dl->AddPolyline(p,7,color,ImDrawFlags_None,size*.065f);
    dl->AddLine(ImVec2(c.x-size*.52f,c.y+size*.37f),
                ImVec2(c.x+size*.52f,c.y+size*.37f),color,size*.08f);
    dl->AddCircleFilled(ImVec2(c.x,c.y-size*.56f),size*.075f,color);
}
void drawHint(ImDrawList* dl,const ImVec2& from,const ImVec2& to) {
    float square=g.width/8.f;
    if (g.mode=="neon") {
        float pad=square*.1f;
        ImVec2 a(from.x-square*.5f+pad,from.y-square*.5f+pad);
        ImVec2 b(from.x+square*.5f-pad,from.y+square*.5f-pad);
        ImVec2 c(to.x-square*.5f+pad,to.y-square*.5f+pad);
        ImVec2 d(to.x+square*.5f-pad,to.y+square*.5f-pad);
        dl->AddRectFilled(a,b,rgba(40,126,225,35),7.f);
        dl->AddRectFilled(c,d,rgba(25,230,137,47),7.f);
        roundedGlow(dl,a,b,rgba(81,194,255),square*.055f);
        roundedGlow(dl,c,d,rgba(56,255,173),square*.075f);
    } else {
        ImVec2 delta(to.x-from.x,to.y-from.y);
        float length=std::sqrt(delta.x*delta.x+delta.y*delta.y);
        if (length<.01f) return;
        ImVec2 u(delta.x/length,delta.y/length);
        ImVec2 tip(to.x-u.x*square*.16f,to.y-u.y*square*.16f);
        for(int i=3;i>=1;i--)
            dl->AddLine(from,tip,rgba(33,255,179,10+i*7),square*(.17f+i*.085f));
        dl->AddLine(from,tip,rgba(59,252,173),std::max(4.f,square*.11f));
        ImVec2 side(-u.y,u.x);
        float tail=square*.34f, wing=square*.16f;
        ImVec2 a(tip.x-u.x*tail+side.x*wing,tip.y-u.y*tail+side.y*wing);
        ImVec2 b(tip.x-u.x*tail-side.x*wing,tip.y-u.y*tail-side.y*wing);
        dl->AddTriangleFilled(tip,a,b,rgba(59,252,173));
        dl->AddCircleFilled(from,square*.08f,rgba(231,255,244));
    }
    char label[32];
    std::snprintf(label,sizeof(label),"%d%%",g.quality);
    std::string badge(label);
    if(g.move.size()==5) {
        badge+="  =";
        badge+=(char)std::toupper((unsigned char)g.move[4]);
    }
    textBadge(dl,to,badge,rgba(52,251,190,220));
}
void drawEffect(ImDrawList* dl,const ImVec2& target) {
    if (g.event.empty() || gFxStart<0) return;
    float life=2.3f/(std::max(60,g.speed)/100.f);
    float t=(float)((seconds()-gFxStart)/life);
    if(t>=1.f) {
        g.event.clear();
        return;
    }
    float size=g.width/8.f;
    float alpha=std::pow(std::max(0.f,1.f-t),1.2f);
    int a=(int)(alpha*235);
    bool mate=g.event=="mate", check=g.event=="check";
    ImU32 col=mate?rgba(255,104,187,a):check?rgba(255,226,96,a):rgba(44,255,194,a);
    float radius=size*(.18f+t*2.0f);
    dl->AddCircle(target,radius,col,60,std::max(2.f,size*.07f*(1.f-t)));
    dl->AddCircle(target,radius*.61f,rgba(237,252,255,a/2),60,2.2f);
    const float PI=3.1415926535f;
    for(int i=0;i<14;i++) {
        float th=2*PI*i/14.f+(i%3)*.11f;
        ImVec2 dir(std::cos(th),std::sin(th));
        float r=size*(.24f+t*(.65f+(i%5)*.18f));
        ImVec2 start(target.x+dir.x*r,target.y+dir.y*r);
        ImVec2 end(start.x+dir.x*size*.25f*(1-t),
                   start.y+dir.y*size*.25f*(1-t));
        dl->AddLine(start,end,col,std::max(1.f,size*.07f*(1-t)));
    }
    if(check||mate) {
        const char* title=mate?"CHECKMATE":"CHECK!";
        ImVec2 ts=ImGui::CalcTextSize(title);
        float xx=std::clamp(target.x,ts.x*.5f+10,g.width-ts.x*.5f-10);
        ImVec2 lo(xx-ts.x*.5f-12,target.y-size*.9f-8);
        ImVec2 hi(xx+ts.x*.5f+12,lo.y+ts.y+13);
        dl->AddRectFilled(lo,hi,rgba(19,21,36,std::min(a,190)),9.f);
        dl->AddRect(lo,hi,col,9.f,0,1.5f);
        dl->AddText(ImVec2(xx-ts.x*.5f,lo.y+5),rgba(255,254,228,a),title);
    }
    if(mate) {
        ImVec2 fly(target.x,target.y-size*(.4f+1.45f*t));
        drawCrown(dl,fly,size*.46f*(1.f+t*.5f),col);
    }
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_opencheps_bridge_BrowserImGuiView_nativeInit(JNIEnv*,jobject) {
    if(gReady) {
        ImGui_ImplOpenGL3_Shutdown();
        ImGui::DestroyContext();
    }
    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io=ImGui::GetIO();
    io.IniFilename=nullptr;
    io.LogFilename=nullptr;
    io.DisplaySize=ImVec2(1,1);
    io.DeltaTime=1.f/60.f;
    io.Fonts->AddFontDefault();
    ImGui::StyleColorsDark();
    gReady=ImGui_ImplOpenGL3_Init("#version 300 es");
    gLastFrame=seconds();
    __android_log_print(ANDROID_LOG_INFO,"Chess/ImGui","ImGui GLES3 initialized=%d",gReady);
}

extern "C" JNIEXPORT void JNICALL
Java_com_opencheps_bridge_BrowserImGuiView_nativeResize(JNIEnv*,jobject,jint w,jint h) {
    g.width=(float)w;
    g.height=(float)h;
}

extern "C" JNIEXPORT void JNICALL
Java_com_opencheps_bridge_BrowserImGuiView_nativeSetFrame(
    JNIEnv* env,jobject,jstring move,jstring mode,jint quality,jboolean flipped,
    jstring event,jint speed,jlong eventId) {
    g.move=fromJni(env,move);
    g.mode=fromJni(env,mode);
    g.quality=(int)quality;
    g.flipped=flipped==JNI_TRUE;
    g.speed=(int)speed;
    if(eventId!=g.eventId) {
        g.eventId=eventId;
        std::string e=fromJni(env,event);
        if(!e.empty()) {
            g.event=e;
            gFxStart=seconds();
        }
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_opencheps_bridge_BrowserImGuiView_nativeRender(JNIEnv*,jobject) {
    glViewport(0,0,(GLsizei)g.width,(GLsizei)g.height);
    glDisable(GL_DEPTH_TEST);
    glClearColor(0.f,0.f,0.f,0.f);
    glClear(GL_COLOR_BUFFER_BIT);
    if(!gReady ||g.width<2.f||g.height<2.f)return;
    double now=seconds();
    ImGuiIO& io=ImGui::GetIO();
    io.DisplaySize=ImVec2(g.width,g.height);
    io.DisplayFramebufferScale=ImVec2(1.f,1.f);
    io.DeltaTime=(float)std::clamp(now-gLastFrame,1.0/300.0,1.0/10.0);
    gLastFrame=now;
    ImGui_ImplOpenGL3_NewFrame();
    ImGui::NewFrame();
    if(g.move.size()>=4 && g.move[0]>='a' && g.move[0]<='h'
            && g.move[1]>='1' && g.move[1]<='8'
            && g.move[2]>='a' && g.move[2]<='h'
            && g.move[3]>='1' && g.move[3]<='8') {
        ImDrawList* dl=ImGui::GetBackgroundDrawList();
        ImVec2 from=cell(g.move.substr(0,2)),to=cell(g.move.substr(2,2));
        drawHint(dl,from,to);
        drawEffect(dl,to);
    }
    ImGui::Render();
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
}
