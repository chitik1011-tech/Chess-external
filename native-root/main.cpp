// ChessRoot: standalone ARM64 root executable, Dear ImGui+GLES3, browser bridge.
// Native SurfaceComposer skipScreenshot, not an APK and not a guaranteed
// invisible capture on every OEM. Uses only local 127.0.0.1:8765.
#include "GraphicsManager.h"
#include "native_surface/ANativeWindowCreator.h"
#include "imgui.h"
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cctype>
#include <cerrno>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>
using namespace std::chrono;
namespace {
constexpr int SIZE=1024;
std::atomic<bool> alive{true};
std::atomic<int> serverFd{-1};
std::mutex lock;
std::string secret;
int port=8765,yOffset=0;
bool demo=false;
struct Data {
 std::string move,mode="arrow",effect,lastEffectMove;
 int quality=100,speed=100;
 bool flipped=false;
 float x=0,y=0,width=0,height=0,viewportW=0;
 long long stamp=0,effectAt=0;
} current;
long long clockMs(){return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();}
void signalHandler(int){alive=false;int s=serverFd.exchange(-1);if(s>=0){shutdown(s,SHUT_RDWR);close(s);}}
std::string generateCode(){
 std::array<unsigned char,6> data{};
 std::ifstream f("/dev/urandom",std::ios::binary);
 if(!f.read((char*)data.data(),data.size()))throw std::runtime_error("/dev/urandom failed");
 char code[13]{};
 for(int i=0;i<6;i++)std::snprintf(code+2*i,3,"%02x",data[i]);
 return code;
}
bool validCode(const std::string& s){if(s.size()!=12)return false;for(char c:s)if(!std::isdigit((unsigned char)c) && !(c>='a'&&c<='f'))return false;return true;}
bool validMove(const std::string& m){
 if(m.empty())return true;
 if(m.size()!=4&&m.size()!=5)return false;
 if(m[0]<'a'||m[0]>'h'||m[2]<'a'||m[2]>'h'||m[1]<'1'||m[1]>'8'||m[3]<'1'||m[3]>'8')return false;
 return m.size()==4||m[4]=='q'||m[4]=='r'||m[4]=='b'||m[4]=='n';
}
size_t field(const std::string& j,const char* key){
 std::string pattern="\""+std::string(key)+"\"";
 size_t p=j.find(pattern);if(p==std::string::npos)return p;
 p=j.find(':',p+pattern.size());if(p==std::string::npos)return p;
 for(++p;p<j.size()&&std::isspace((unsigned char)j[p]);++p){}
 return p;
}
std::string str(const std::string& j,const char* key){
 auto p=field(j,key);if(p==std::string::npos||p>=j.size()||j[p]!='"')return {};
 std::string out;for(++p;p<j.size()&&out.size()<128;++p){
  if(j[p]=='"')return out;
  if(j[p]=='\\'||(unsigned char)j[p]<32)return {};
  out+=j[p];
 }
 return {};
}
float num(const std::string& j,const char* key,float fallback=0){
 auto p=field(j,key);if(p==std::string::npos)return fallback;
 char* end=nullptr;errno=0;
 float val=std::strtof(j.c_str()+p,&end);
 return errno||end==j.c_str()+p||!std::isfinite(val)?fallback:val;
}
bool boolean(const std::string& j,const char* key){
 auto p=field(j,key);return p!=std::string::npos&&j.compare(p,4,"true")==0;
}
void reply(int fd,int status,const char* message){
 const bool ok=status==200;
 std::string body=std::string("{\"ok\":")+(ok?"true":"false")+",\"message\":\""+message+"\"}";
 const char* title=status==200?"OK":status==204?"No Content":status==403?"Forbidden":status==400?"Bad Request":"Not Found";
 std::string head="HTTP/1.1 "+std::to_string(status)+" "+title+"\r\n"
   "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
   "Access-Control-Allow-Headers: Content-Type\r\nAccess-Control-Allow-Private-Network: true\r\n"
   "Cache-Control: no-store\r\nContent-Type: application/json\r\nConnection: close\r\n"
   "Content-Length: "+std::to_string(status==204?0:body.size())+"\r\n\r\n";
 send(fd,head.data(),head.size(),MSG_NOSIGNAL);
 if(status!=204)send(fd,body.data(),body.size(),MSG_NOSIGNAL);
}
void client(int fd){
 timeval limit{1,500000};setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&limit,sizeof(limit));
 std::string data;char buf[1024];size_t end=std::string::npos;
 while(data.size()<8192){
  int n=recv(fd,buf,sizeof(buf),0);if(n<=0)return;
  data.append(buf,n);end=data.find("\r\n\r\n");if(end!=std::string::npos)break;
 }
 if(end==std::string::npos||end>4096){reply(fd,400,"bad-headers");return;}
 size_t eol=data.find("\r\n");if(eol==std::string::npos){reply(fd,400,"bad-request");return;}
 std::string request=data.substr(0,eol);
 if(request.rfind("OPTIONS /frame HTTP/1.",0)==0){reply(fd,204,"preflight");return;}
 if(request.rfind("GET /health HTTP/1.",0)==0){reply(fd,200,"root-imgui-ready");return;}
 if(request.rfind("POST /frame HTTP/1.",0)!=0){reply(fd,404,"not-found");return;}
 size_t length=0,p=eol+2;
 while(p<end){
  size_t x=data.find("\r\n",p);if(x==std::string::npos||x>end)break;
  std::string h=data.substr(p,x-p);
  std::transform(h.begin(),h.end(),h.begin(),[](unsigned char c){return (char)std::tolower(c);});
  if(h.rfind("content-length:",0)==0){
   try{length=(size_t)std::stoul(h.substr(15));}catch(...){length=0;}
  }
  p=x+2;
 }
 if(length<2||length>4096){reply(fd,400,"invalid-body-size");return;}
 std::string json=data.substr(end+4);
 while(json.size()<length){
  int n=recv(fd,buf,std::min((size_t)sizeof(buf),length-json.size()),0);
  if(n<=0)return;json.append(buf,n);
 }
 json.resize(length);
 if(str(json,"token")!=secret){reply(fd,403,"bad-pair-code");return;}
 Data next;
 next.move=str(json,"move");
 if(!validMove(next.move)){reply(fd,400,"bad-move");return;}
 next.mode=str(json,"mode");next.effect=str(json,"event");
 if(!next.effect.empty()&&next.effect!="capture"&&next.effect!="check"&&next.effect!="mate"){
  reply(fd,400,"bad-event");return;
 }
 next.quality=(int)std::clamp(num(json,"quality",100),0.f,100.f);
 next.speed=(int)std::clamp(num(json,"speed",100),60.f,180.f);
 next.flipped=boolean(json,"flipped");
 next.x=num(json,"x",-9999);next.y=num(json,"y",-9999);
 next.width=num(json,"width");next.height=num(json,"height");next.viewportW=num(json,"viewportW");
 if(next.viewportW<100||next.width<80||next.height<80||next.width>next.viewportW*1.2f
     ||next.x<-5000||next.y<-5000){reply(fd,400,"bad-geometry");return;}
 next.stamp=clockMs();
 {
  std::lock_guard<std::mutex> hold(lock);
  if(next.effect.empty()&&clockMs()-current.effectAt<2000){
   next.effect=current.effect;next.effectAt=current.effectAt;next.lastEffectMove=current.lastEffectMove;
  } else if(!next.effect.empty()){
   next.effectAt=clockMs();next.lastEffectMove=next.move;
  }
  current=next;
 }
 reply(fd,200,"accepted");
}
void serve(){
 int fd=socket(AF_INET,SOCK_STREAM,0);
 if(fd<0){std::perror("socket");alive=false;return;}
 int yes=1;setsockopt(fd,SOL_SOCKET,SO_REUSEADDR,&yes,sizeof(yes));
 sockaddr_in a{};a.sin_family=AF_INET;a.sin_addr.s_addr=htonl(INADDR_LOOPBACK);a.sin_port=htons(port);
 if(bind(fd,(sockaddr*)&a,sizeof(a))||listen(fd,8)){
  std::perror("127.0.0.1:8765 bind (stop old APK bridge first)");
  close(fd);alive=false;return;
 }
 serverFd=fd;
 std::printf("Bridge ready: http://127.0.0.1:%d\n",port);std::fflush(stdout);
 while(alive){int c=accept(fd,nullptr,nullptr);if(c<0){if(errno==EINTR)continue;break;}client(c);close(c);}
 if(serverFd.exchange(-1)==fd)close(fd);
}
ImVec2 point(const std::string& square,bool flip){
 int x=square[0]-'a',y=square[1]-'1';
 return ImVec2((flip?7-x:x)+.5f,(flip?y:7-y)+.5f);
}
void paintHint(ImDrawList* d,const Data& f){
 if(f.move.size()<4)return;
 ImVec2 a=point(f.move.substr(0,2),f.flipped),b=point(f.move.substr(2,2),f.flipped);
 float u=SIZE/8.f;a.x*=u;a.y*=u;b.x*=u;b.y*=u;
 if(f.mode=="neon"){
  for(int i=0;i<2;i++){
   ImVec2 p=i?b:a;float pad=13;
   ImU32 color=i?IM_COL32(55,255,164,246):IM_COL32(70,195,255,240);
   d->AddRectFilled({p.x-u/2+pad,p.y-u/2+pad},{p.x+u/2-pad,p.y+u/2-pad},
                    i?IM_COL32(40,255,171,55):IM_COL32(55,144,255,45),12.f);
   d->AddRect({p.x-u/2+pad,p.y-u/2+pad},{p.x+u/2-pad,p.y+u/2-pad},color,12.f,0,7.f);
  }
 } else {
  float dx=b.x-a.x,dy=b.y-a.y,m=std::sqrt(dx*dx+dy*dy);
  if(m>1){
   float ux=dx/m,uy=dy/m;ImVec2 tip(b.x-ux*21,b.y-uy*21);
   d->AddLine(a,tip,IM_COL32(26,242,174,42),28);
   d->AddLine(a,tip,IM_COL32(50,250,185,255),10);
   d->AddTriangleFilled(tip,{tip.x-ux*43-uy*17,tip.y-uy*43+ux*17},
                            {tip.x-ux*43+uy*17,tip.y-uy*43-ux*17},IM_COL32(50,250,185,255));
  }
 }
 std::string text=std::to_string(f.quality)+"%";
 if(f.move.size()==5){text+=" =";text+=(char)std::toupper((unsigned char)f.move[4]);}
 ImVec2 ts=ImGui::CalcTextSize(text.c_str());
 float tx=std::clamp(b.x,ts.x/2+14,(float)SIZE-ts.x/2-14),ty=std::max(34.f,b.y-u*.65f);
 d->AddRectFilled({tx-ts.x/2-15,ty-ts.y-9},{tx+ts.x/2+15,ty+9},IM_COL32(8,35,34,240),10);
 d->AddRect({tx-ts.x/2-15,ty-ts.y-9},{tx+ts.x/2+15,ty+9},IM_COL32(50,255,183,220),10,0,2);
 d->AddText({tx-ts.x/2,ty-ts.y},IM_COL32_WHITE,text.c_str());
}
void paintFx(ImDrawList* d,const Data& f,long long now){
 std::string m=f.lastEffectMove.empty()?f.move:f.lastEffectMove;
 if(f.effect.empty()||m.size()<4||now-f.effectAt>2400)return;
 float t=std::clamp((float)(now-f.effectAt)*f.speed/100.f/1500.f,0.f,1.f);
 if(t>=1)return;
 ImVec2 p=point(m.substr(2,2),f.flipped);p.x*=SIZE/8.f;p.y*=SIZE/8.f;
 int alpha=(int)(215*(1-t));
 bool mate=f.effect=="mate",check=f.effect=="check";
 ImU32 color=mate?IM_COL32(255,110,186,alpha):
               check?IM_COL32(255,228,85,alpha):IM_COL32(60,251,203,alpha);
 d->AddCircle(p,30+220*t,color,52,6*(1-t)+1);
 for(int i=0;i<16;i++){
  float angle=6.2831853f*i/16.f;
  ImVec2 direction(std::cos(angle),std::sin(angle));
  float length=50+t*(90+(i%5)*23);
  d->AddLine({p.x+direction.x*length,p.y+direction.y*length},
             {p.x+direction.x*(length+30*(1-t)),p.y+direction.y*(length+30*(1-t))},
             color,5*(1-t)+1);
 }
 if(check||mate){
  const char* name=mate?"CHECKMATE":"CHECK";
  ImVec2 ts=ImGui::CalcTextSize(name);
  float x=std::clamp(p.x,ts.x/2+20,(float)SIZE-ts.x/2-20);
  float y=p.y-120;
  d->AddRectFilled({x-ts.x/2-15,y-10},{x+ts.x/2+15,y+ts.y+11},IM_COL32(20,21,31,190),11);
  d->AddRect({x-ts.x/2-15,y-10},{x+ts.x/2+15,y+ts.y+11},color,11,0,2);
  d->AddText({x-ts.x/2,y},IM_COL32(255,255,225,alpha),name);
 }
 if(mate){ // stylized king crown flying away
  ImVec2 c(p.x,p.y-50-140*t);
  d->AddLine({c.x-39,c.y+19},{c.x+39,c.y+19},color,6);
  d->AddTriangleFilled({c.x-39,c.y-17},{c.x-16,c.y+15},{c.x,c.y-37},color);
  d->AddTriangleFilled({c.x,c.y-37},{c.x+16,c.y+15},{c.x+39,c.y-17},color);
 }
}
void help(){
 std::puts("Chess Root ImGui — Android ARM64 root executable");
 std::puts("  --token 12hex      Pairing code (otherwise generated)");
 std::puts("  --port 8765        Local browser bridge port");
 std::puts("  --y-offset 130     Move overlay down in SCREEN pixels");
 std::puts("  --demo             Persistent sample neon arrow for testing");
 std::puts("  --help             Display this help");
}
}
int main(int argc,char** argv){
 try{
  for(int i=1;i<argc;i++){
   std::string a=argv[i];
   if(a=="--help"){help();return 0;}
   if(a=="--demo"){demo=true;continue;}
   if(a=="--token"&&i+1<argc){secret=argv[++i];continue;}
   if(a=="--port"&&i+1<argc){port=std::atoi(argv[++i]);continue;}
   if(a=="--y-offset"&&i+1<argc){yOffset=std::atoi(argv[++i]);continue;}
   std::fprintf(stderr,"Unknown argument: %s\n",a.c_str());return 2;
  }
  if(!secret.empty()&&!validCode(secret)){std::fprintf(stderr,"Bad token: 12 lowercase hex required\n");return 2;}
  if(port<1024||port>65535){std::fprintf(stderr,"Port out of range\n");return 2;}
  if(getuid()!=0){std::fprintf(stderr,"Root shell required: su -c ...\n");return 3;}
  if(secret.empty())secret=generateCode();
 }catch(const std::exception& e){std::fprintf(stderr,"Init error: %s\n",e.what());return 4;}
 std::signal(SIGTERM,signalHandler);std::signal(SIGINT,signalHandler);
 std::printf("Chess Root ImGui | PID %d | pairing code: %s\n",getpid(),secret.c_str());
 std::printf("skipScreenshot requested; OEM may show black region in recordings.\n");
 std::fflush(stdout);
 auto display=android::ANativeWindowCreator::GetDisplayInfo();
 if(display.width<200||display.height<200){std::fprintf(stderr,"SurfaceComposer ABI incompatible with this ROM\n");return 5;}
 auto gfx=GraphicsManager::getGraphicsInterface(GraphicsManager::OPENGL);
 if(!gfx)return 6;
 // Fourth parameter TRUE requests a screenshot-excluded SurfaceComposer layer.
 ANativeWindow* surface=android::ANativeWindowCreator::Create("Chess Root ImGui",SIZE,SIZE,true);
 if(!surface){std::fprintf(stderr,"Secure layer creation failed\n");return 7;}
 if(!gfx->Init_Render(surface,SIZE,SIZE)){std::fprintf(stderr,"EGL init failed\n");return 8;}
 ImGui::StyleColorsDark();
 android::ANativeWindowCreator::SetPosition(surface,-3000,-3000);
 std::thread backend(serve);
 bool showing=false;
 if(demo){
  Data d;d.move="e2e4";d.mode="neon";d.quality=76;d.viewportW=display.width;
  d.width=display.width;d.height=display.width;d.y=300;d.stamp=clockMs();
  std::lock_guard<std::mutex> hold(lock);current=d;
 }
 while(alive){
  Data f;{std::lock_guard<std::mutex> hold(lock);f=current;}
  long long now=clockMs();
  bool fresh=demo||now-f.stamp<3000;
  bool fx=!f.effect.empty()&&now-f.effectAt<2100;
  bool wanted=(fresh&&!f.move.empty())||fx;
  if(wanted&&f.viewportW>100&&f.width>100){
   float ratio=display.width/f.viewportW;
   float physicalWidth=std::clamp(f.width*ratio,100.f,(float)display.width);
   float scale=physicalWidth/SIZE;
   int x=std::clamp((int)std::lround(f.x*ratio),0,display.width-80);
   int y=std::clamp((int)std::lround(f.y*ratio)+yOffset,0,display.height-80);
   android::ANativeWindowCreator::SetLayerGeometry(surface,SIZE,SIZE,scale);
   android::ANativeWindowCreator::SetPosition(surface,x,y);
   showing=true;
  }else if(showing){android::ANativeWindowCreator::SetPosition(surface,-3000,-3000);showing=false;}
  gfx->NewFrame(false);
  if(showing){
   ImDrawList* d=ImGui::GetBackgroundDrawList();
   if(fresh)paintHint(d,f);
   if(fx)paintFx(d,f,now);
  }
  gfx->EndFrame();
  std::this_thread::sleep_for(std::chrono::milliseconds(33));
 }
 int fd=serverFd.exchange(-1);if(fd>=0){shutdown(fd,SHUT_RDWR);close(fd);}
 if(backend.joinable())backend.join();
 gfx->Shutdown();android::ANativeWindowCreator::Destroy(surface);
 return 0;
}
