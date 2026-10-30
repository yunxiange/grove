import{h as qt}from"./chunk-3VK4XBCB.js";import{a as Qt}from"./chunk-VKQAYL2D.js";import{b as Xt,c as Jt}from"./chunk-LXGBQ3GU.js";import{a as Zt}from"./chunk-YNPSB2VR.js";import"./chunk-R72D66RC.js";import"./chunk-RKZIRJ5A.js";import"./chunk-B2AZGH5U.js";import"./chunk-BI7GWSVN.js";import"./chunk-U5NK4UYN.js";import"./chunk-Y32BBXU3.js";import"./chunk-BL5YAXTS.js";import"./chunk-ABWKCKTA.js";import"./chunk-55AZIQ2M.js";import{b as Ut,c as Ht,d as zt,e as Kt}from"./chunk-6NDAIVIK.js";import{g as Wt,q as jt}from"./chunk-HSCO3YIN.js";import"./chunk-5IVBZGPZ.js";import{N as U,T as Ft,U as Pt,V as Bt,W as Gt,X as Yt,Y as Vt,Z as Mt,_ as N}from"./chunk-WR5N2H2H.js";import{a as $t}from"./chunk-RB27NJXS.js";import{b as E,h as St}from"./chunk-3CDHY5O5.js";import{a as p}from"./chunk-YUSHYV7C.js";import"./chunk-AIYMQX7V.js";var xt=function(){var t=p(function(j,c,f,o){for(f=f||{},o=j.length;o--;f[j[o]]=c);return f},"o"),e=[1,2],s=[1,3],a=[1,4],r=[2,4],n=[1,9],l=[1,11],u=[1,16],d=[1,17],y=[1,18],m=[1,19],k=[1,33],I=[1,20],L=[1,21],w=[1,22],$=[1,23],F=[1,24],D=[1,26],g=[1,27],v=[1,28],P=[1,29],G=[1,30],C=[1,31],Y=[1,32],B=[1,35],ot=[1,36],lt=[1,37],ct=[1,38],J=[1,34],S=[1,4,5,16,17,19,21,22,24,25,26,27,28,29,33,35,37,38,41,45,48,51,52,53,54,57],ht=[1,4,5,14,15,16,17,19,21,22,24,25,26,27,28,29,33,35,37,38,39,40,41,45,48,51,52,53,54,57],Ot=[4,5,16,17,19,21,22,24,25,26,27,28,29,33,35,37,38,41,45,48,51,52,53,54,57],Tt={trace:p(function(){},"trace"),yy:{},symbols_:{error:2,start:3,SPACE:4,NL:5,SD:6,document:7,line:8,statement:9,classDefStatement:10,styleStatement:11,cssClassStatement:12,idStatement:13,DESCR:14,"-->":15,HIDE_EMPTY:16,scale:17,WIDTH:18,COMPOSIT_STATE:19,STRUCT_START:20,STRUCT_STOP:21,STATE_DESCR:22,AS:23,ID:24,FORK:25,JOIN:26,CHOICE:27,CONCURRENT:28,note:29,notePosition:30,NOTE_TEXT:31,direction:32,acc_title:33,acc_title_value:34,acc_descr:35,acc_descr_value:36,acc_descr_multiline_value:37,CLICK:38,STRING:39,HREF:40,classDef:41,CLASSDEF_ID:42,CLASSDEF_STYLEOPTS:43,DEFAULT:44,style:45,STYLE_IDS:46,STYLEDEF_STYLEOPTS:47,class:48,CLASSENTITY_IDS:49,STYLECLASS:50,direction_tb:51,direction_bt:52,direction_rl:53,direction_lr:54,eol:55,";":56,EDGE_STATE:57,STYLE_SEPARATOR:58,left_of:59,right_of:60,$accept:0,$end:1},terminals_:{2:"error",4:"SPACE",5:"NL",6:"SD",14:"DESCR",15:"-->",16:"HIDE_EMPTY",17:"scale",18:"WIDTH",19:"COMPOSIT_STATE",20:"STRUCT_START",21:"STRUCT_STOP",22:"STATE_DESCR",23:"AS",24:"ID",25:"FORK",26:"JOIN",27:"CHOICE",28:"CONCURRENT",29:"note",31:"NOTE_TEXT",33:"acc_title",34:"acc_title_value",35:"acc_descr",36:"acc_descr_value",37:"acc_descr_multiline_value",38:"CLICK",39:"STRING",40:"HREF",41:"classDef",42:"CLASSDEF_ID",43:"CLASSDEF_STYLEOPTS",44:"DEFAULT",45:"style",46:"STYLE_IDS",47:"STYLEDEF_STYLEOPTS",48:"class",49:"CLASSENTITY_IDS",50:"STYLECLASS",51:"direction_tb",52:"direction_bt",53:"direction_rl",54:"direction_lr",56:";",57:"EDGE_STATE",58:"STYLE_SEPARATOR",59:"left_of",60:"right_of"},productions_:[0,[3,2],[3,2],[3,2],[7,0],[7,2],[8,2],[8,1],[8,1],[9,1],[9,1],[9,1],[9,1],[9,2],[9,3],[9,4],[9,1],[9,2],[9,1],[9,4],[9,3],[9,6],[9,1],[9,1],[9,1],[9,1],[9,4],[9,4],[9,1],[9,2],[9,2],[9,1],[9,5],[9,5],[10,3],[10,3],[11,3],[12,3],[32,1],[32,1],[32,1],[32,1],[55,1],[55,1],[13,1],[13,1],[13,3],[13,3],[30,1],[30,1]],performAction:p(function(c,f,o,b,T,i,q){var h=i.length-1;switch(T){case 3:return b.setRootDoc(i[h]),i[h];break;case 4:this.$=[];break;case 5:i[h]!="nl"&&(i[h-1].push(i[h]),this.$=i[h-1]);break;case 6:case 7:this.$=i[h];break;case 8:this.$="nl";break;case 12:this.$=i[h];break;case 13:let ut=i[h-1];ut.description=b.trimColon(i[h]),this.$=ut;break;case 14:this.$={stmt:"relation",state1:i[h-2],state2:i[h]};break;case 15:let ft=b.trimColon(i[h]);this.$={stmt:"relation",state1:i[h-3],state2:i[h-1],description:ft};break;case 19:this.$={stmt:"state",id:i[h-3],type:"default",description:"",doc:i[h-1]};break;case 20:var V=i[h],M=i[h-2].trim();if(i[h].match(":")){var et=i[h].split(":");V=et[0],M=[M,et[1]]}this.$={stmt:"state",id:V,type:"default",description:M};break;case 21:this.$={stmt:"state",id:i[h-3],type:"default",description:i[h-5],doc:i[h-1]};break;case 22:this.$={stmt:"state",id:i[h],type:"fork"};break;case 23:this.$={stmt:"state",id:i[h],type:"join"};break;case 24:this.$={stmt:"state",id:i[h],type:"choice"};break;case 25:this.$={stmt:"state",id:b.getDividerId(),type:"divider"};break;case 26:this.$={stmt:"state",id:i[h-1].trim(),note:{position:i[h-2].trim(),text:i[h].trim()}};break;case 29:this.$=i[h].trim(),b.setAccTitle(this.$);break;case 30:case 31:this.$=i[h].trim(),b.setAccDescription(this.$);break;case 32:this.$={stmt:"click",id:i[h-3],url:i[h-2],tooltip:i[h-1]};break;case 33:this.$={stmt:"click",id:i[h-3],url:i[h-1],tooltip:""};break;case 34:case 35:this.$={stmt:"classDef",id:i[h-1].trim(),classes:i[h].trim()};break;case 36:this.$={stmt:"style",id:i[h-1].trim(),styleClass:i[h].trim()};break;case 37:this.$={stmt:"applyClass",id:i[h-1].trim(),styleClass:i[h].trim()};break;case 38:b.setDirection("TB"),this.$={stmt:"dir",value:"TB"};break;case 39:b.setDirection("BT"),this.$={stmt:"dir",value:"BT"};break;case 40:b.setDirection("RL"),this.$={stmt:"dir",value:"RL"};break;case 41:b.setDirection("LR"),this.$={stmt:"dir",value:"LR"};break;case 44:case 45:this.$={stmt:"state",id:i[h].trim(),type:"default",description:""};break;case 46:this.$={stmt:"state",id:i[h-2].trim(),classes:[i[h].trim()],type:"default",description:""};break;case 47:this.$={stmt:"state",id:i[h-2].trim(),classes:[i[h].trim()],type:"default",description:""};break}},"anonymous"),table:[{3:1,4:e,5:s,6:a},{1:[3]},{3:5,4:e,5:s,6:a},{3:6,4:e,5:s,6:a},t([1,4,5,16,17,19,22,24,25,26,27,28,29,33,35,37,38,41,45,48,51,52,53,54,57],r,{7:7}),{1:[2,1]},{1:[2,2]},{1:[2,3],4:n,5:l,8:8,9:10,10:12,11:13,12:14,13:15,16:u,17:d,19:y,22:m,24:k,25:I,26:L,27:w,28:$,29:F,32:25,33:D,35:g,37:v,38:P,41:G,45:C,48:Y,51:B,52:ot,53:lt,54:ct,57:J},t(S,[2,5]),{9:39,10:12,11:13,12:14,13:15,16:u,17:d,19:y,22:m,24:k,25:I,26:L,27:w,28:$,29:F,32:25,33:D,35:g,37:v,38:P,41:G,45:C,48:Y,51:B,52:ot,53:lt,54:ct,57:J},t(S,[2,7]),t(S,[2,8]),t(S,[2,9]),t(S,[2,10]),t(S,[2,11]),t(S,[2,12],{14:[1,40],15:[1,41]}),t(S,[2,16]),{18:[1,42]},t(S,[2,18],{20:[1,43]}),{23:[1,44]},t(S,[2,22]),t(S,[2,23]),t(S,[2,24]),t(S,[2,25]),{30:45,31:[1,46],59:[1,47],60:[1,48]},t(S,[2,28]),{34:[1,49]},{36:[1,50]},t(S,[2,31]),{13:51,24:k,57:J},{42:[1,52],44:[1,53]},{46:[1,54]},{49:[1,55]},t(ht,[2,44],{58:[1,56]}),t(ht,[2,45],{58:[1,57]}),t(S,[2,38]),t(S,[2,39]),t(S,[2,40]),t(S,[2,41]),t(S,[2,6]),t(S,[2,13]),{13:58,24:k,57:J},t(S,[2,17]),t(Ot,r,{7:59}),{24:[1,60]},{24:[1,61]},{23:[1,62]},{24:[2,48]},{24:[2,49]},t(S,[2,29]),t(S,[2,30]),{39:[1,63],40:[1,64]},{43:[1,65]},{43:[1,66]},{47:[1,67]},{50:[1,68]},{24:[1,69]},{24:[1,70]},t(S,[2,14],{14:[1,71]}),{4:n,5:l,8:8,9:10,10:12,11:13,12:14,13:15,16:u,17:d,19:y,21:[1,72],22:m,24:k,25:I,26:L,27:w,28:$,29:F,32:25,33:D,35:g,37:v,38:P,41:G,45:C,48:Y,51:B,52:ot,53:lt,54:ct,57:J},t(S,[2,20],{20:[1,73]}),{31:[1,74]},{24:[1,75]},{39:[1,76]},{39:[1,77]},t(S,[2,34]),t(S,[2,35]),t(S,[2,36]),t(S,[2,37]),t(ht,[2,46]),t(ht,[2,47]),t(S,[2,15]),t(S,[2,19]),t(Ot,r,{7:78}),t(S,[2,26]),t(S,[2,27]),{5:[1,79]},{5:[1,80]},{4:n,5:l,8:8,9:10,10:12,11:13,12:14,13:15,16:u,17:d,19:y,21:[1,81],22:m,24:k,25:I,26:L,27:w,28:$,29:F,32:25,33:D,35:g,37:v,38:P,41:G,45:C,48:Y,51:B,52:ot,53:lt,54:ct,57:J},t(S,[2,32]),t(S,[2,33]),t(S,[2,21])],defaultActions:{5:[2,1],6:[2,2],47:[2,48],48:[2,49]},parseError:p(function(c,f){if(f.recoverable)this.trace(c);else{var o=new Error(c);throw o.hash=f,o}},"parseError"),parse:p(function(c){var f=this,o=[0],b=[],T=[null],i=[],q=this.table,h="",V=0,M=0,et=0,ut=2,ft=1,ke=i.slice.call(arguments,1),_=Object.create(this.lexer),z={yy:{}};for(var kt in this.yy)Object.prototype.hasOwnProperty.call(this.yy,kt)&&(z.yy[kt]=this.yy[kt]);_.setInput(c,z.yy),z.yy.lexer=_,z.yy.parser=this,typeof _.yylloc>"u"&&(_.yylloc={});var Et=_.yylloc;i.push(Et);var Ee=_.options&&_.options.ranges;typeof z.yy.parseError=="function"?this.parseError=z.yy.parseError:this.parseError=Object.getPrototypeOf(this).parseError;function _e(O){o.length=o.length-2*O,T.length=T.length-O,i.length=i.length-O}p(_e,"popStack");function Rt(){var O;return O=b.pop()||_.lex()||ft,typeof O!="number"&&(O instanceof Array&&(b=O,O=b.pop()),O=f.symbols_[O]||O),O}p(Rt,"lex");for(var A,_t,K,R,es,vt,Q={},pt,W,Nt,gt;;){if(K=o[o.length-1],this.defaultActions[K]?R=this.defaultActions[K]:((A===null||typeof A>"u")&&(A=Rt()),R=q[K]&&q[K][A]),typeof R>"u"||!R.length||!R[0]){var Dt="";gt=[];for(pt in q[K])this.terminals_[pt]&&pt>ut&&gt.push("'"+this.terminals_[pt]+"'");_.showPosition?Dt="Parse error on line "+(V+1)+`:
`+_.showPosition()+`
Expecting `+gt.join(", ")+", got '"+(this.terminals_[A]||A)+"'":Dt="Parse error on line "+(V+1)+": Unexpected "+(A==ft?"end of input":"'"+(this.terminals_[A]||A)+"'"),this.parseError(Dt,{text:_.match,token:this.terminals_[A]||A,line:_.yylineno,loc:Et,expected:gt})}if(R[0]instanceof Array&&R.length>1)throw new Error("Parse Error: multiple actions possible at state: "+K+", token: "+A);switch(R[0]){case 1:o.push(A),T.push(_.yytext),i.push(_.yylloc),o.push(R[1]),A=null,_t?(A=_t,_t=null):(M=_.yyleng,h=_.yytext,V=_.yylineno,Et=_.yylloc,et>0&&et--);break;case 2:if(W=this.productions_[R[1]][1],Q.$=T[T.length-W],Q._$={first_line:i[i.length-(W||1)].first_line,last_line:i[i.length-1].last_line,first_column:i[i.length-(W||1)].first_column,last_column:i[i.length-1].last_column},Ee&&(Q._$.range=[i[i.length-(W||1)].range[0],i[i.length-1].range[1]]),vt=this.performAction.apply(Q,[h,M,V,z.yy,R[1],T,i].concat(ke)),typeof vt<"u")return vt;W&&(o=o.slice(0,-1*W*2),T=T.slice(0,-1*W),i=i.slice(0,-1*W)),o.push(this.productions_[R[1]][0]),T.push(Q.$),i.push(Q._$),Nt=q[o[o.length-2]][o[o.length-1]],o.push(Nt);break;case 3:return!0}}return!0},"parse")},Te=function(){var j={EOF:1,parseError:p(function(f,o){if(this.yy.parser)this.yy.parser.parseError(f,o);else throw new Error(f)},"parseError"),setInput:p(function(c,f){return this.yy=f||this.yy||{},this._input=c,this._more=this._backtrack=this.done=!1,this.yylineno=this.yyleng=0,this.yytext=this.matched=this.match="",this.conditionStack=["INITIAL"],this.yylloc={first_line:1,first_column:0,last_line:1,last_column:0},this.options.ranges&&(this.yylloc.range=[0,0]),this.offset=0,this},"setInput"),input:p(function(){var c=this._input[0];this.yytext+=c,this.yyleng++,this.offset++,this.match+=c,this.matched+=c;var f=c.match(/(?:\r\n?|\n).*/g);return f?(this.yylineno++,this.yylloc.last_line++):this.yylloc.last_column++,this.options.ranges&&this.yylloc.range[1]++,this._input=this._input.slice(1),c},"input"),unput:p(function(c){var f=c.length,o=c.split(/(?:\r\n?|\n)/g);this._input=c+this._input,this.yytext=this.yytext.substr(0,this.yytext.length-f),this.offset-=f;var b=this.match.split(/(?:\r\n?|\n)/g);this.match=this.match.substr(0,this.match.length-1),this.matched=this.matched.substr(0,this.matched.length-1),o.length-1&&(this.yylineno-=o.length-1);var T=this.yylloc.range;return this.yylloc={first_line:this.yylloc.first_line,last_line:this.yylineno+1,first_column:this.yylloc.first_column,last_column:o?(o.length===b.length?this.yylloc.first_column:0)+b[b.length-o.length].length-o[0].length:this.yylloc.first_column-f},this.options.ranges&&(this.yylloc.range=[T[0],T[0]+this.yyleng-f]),this.yyleng=this.yytext.length,this},"unput"),more:p(function(){return this._more=!0,this},"more"),reject:p(function(){if(this.options.backtrack_lexer)this._backtrack=!0;else return this.parseError("Lexical error on line "+(this.yylineno+1)+`. You can only invoke reject() in the lexer when the lexer is of the backtracking persuasion (options.backtrack_lexer = true).
`+this.showPosition(),{text:"",token:null,line:this.yylineno});return this},"reject"),less:p(function(c){this.unput(this.match.slice(c))},"less"),pastInput:p(function(){var c=this.matched.substr(0,this.matched.length-this.match.length);return(c.length>20?"...":"")+c.substr(-20).replace(/\n/g,"")},"pastInput"),upcomingInput:p(function(){var c=this.match;return c.length<20&&(c+=this._input.substr(0,20-c.length)),(c.substr(0,20)+(c.length>20?"...":"")).replace(/\n/g,"")},"upcomingInput"),showPosition:p(function(){var c=this.pastInput(),f=new Array(c.length+1).join("-");return c+this.upcomingInput()+`
`+f+"^"},"showPosition"),test_match:p(function(c,f){var o,b,T;if(this.options.backtrack_lexer&&(T={yylineno:this.yylineno,yylloc:{first_line:this.yylloc.first_line,last_line:this.last_line,first_column:this.yylloc.first_column,last_column:this.yylloc.last_column},yytext:this.yytext,match:this.match,matches:this.matches,matched:this.matched,yyleng:this.yyleng,offset:this.offset,_more:this._more,_input:this._input,yy:this.yy,conditionStack:this.conditionStack.slice(0),done:this.done},this.options.ranges&&(T.yylloc.range=this.yylloc.range.slice(0))),b=c[0].match(/(?:\r\n?|\n).*/g),b&&(this.yylineno+=b.length),this.yylloc={first_line:this.yylloc.last_line,last_line:this.yylineno+1,first_column:this.yylloc.last_column,last_column:b?b[b.length-1].length-b[b.length-1].match(/\r?\n?/)[0].length:this.yylloc.last_column+c[0].length},this.yytext+=c[0],this.match+=c[0],this.matches=c,this.yyleng=this.yytext.length,this.options.ranges&&(this.yylloc.range=[this.offset,this.offset+=this.yyleng]),this._more=!1,this._backtrack=!1,this._input=this._input.slice(c[0].length),this.matched+=c[0],o=this.performAction.call(this,this.yy,this,f,this.conditionStack[this.conditionStack.length-1]),this.done&&this._input&&(this.done=!1),o)return o;if(this._backtrack){for(var i in T)this[i]=T[i];return!1}return!1},"test_match"),next:p(function(){if(this.done)return this.EOF;this._input||(this.done=!0);var c,f,o,b;this._more||(this.yytext="",this.match="");for(var T=this._currentRules(),i=0;i<T.length;i++)if(o=this._input.match(this.rules[T[i]]),o&&(!f||o[0].length>f[0].length)){if(f=o,b=i,this.options.backtrack_lexer){if(c=this.test_match(o,T[i]),c!==!1)return c;if(this._backtrack){f=!1;continue}else return!1}else if(!this.options.flex)break}return f?(c=this.test_match(f,T[b]),c!==!1?c:!1):this._input===""?this.EOF:this.parseError("Lexical error on line "+(this.yylineno+1)+`. Unrecognized text.
`+this.showPosition(),{text:"",token:null,line:this.yylineno})},"next"),lex:p(function(){var f=this.next();return f||this.lex()},"lex"),begin:p(function(f){this.conditionStack.push(f)},"begin"),popState:p(function(){var f=this.conditionStack.length-1;return f>0?this.conditionStack.pop():this.conditionStack[0]},"popState"),_currentRules:p(function(){return this.conditionStack.length&&this.conditionStack[this.conditionStack.length-1]?this.conditions[this.conditionStack[this.conditionStack.length-1]].rules:this.conditions.INITIAL.rules},"_currentRules"),topState:p(function(f){return f=this.conditionStack.length-1-Math.abs(f||0),f>=0?this.conditionStack[f]:"INITIAL"},"topState"),pushState:p(function(f){this.begin(f)},"pushState"),stateStackSize:p(function(){return this.conditionStack.length},"stateStackSize"),options:{"case-insensitive":!0},performAction:p(function(f,o,b,T){function i(){let h=o.yytext.indexOf("%%");if(h===0)return!1;if(h>0){let V=o.yytext.slice(0,h),M=o.yytext.slice(h);M&&f.lexer.unput(M),o.yytext=V}return!0}p(i,"processId");var q=T;switch(b){case 0:return 38;case 1:return 40;case 2:return 39;case 3:return 44;case 4:return 51;case 5:return 52;case 6:return 53;case 7:return 54;case 8:return 5;case 9:break;case 10:break;case 11:break;case 12:break;case 13:return this.pushState("SCALE"),17;break;case 14:return 18;case 15:this.popState();break;case 16:return this.begin("acc_title"),33;break;case 17:return this.popState(),"acc_title_value";break;case 18:return this.begin("acc_descr"),35;break;case 19:return this.popState(),"acc_descr_value";break;case 20:this.begin("acc_descr_multiline");break;case 21:this.popState();break;case 22:return"acc_descr_multiline_value";case 23:return this.pushState("CLASSDEF"),41;break;case 24:return this.popState(),this.pushState("CLASSDEFID"),"DEFAULT_CLASSDEF_ID";break;case 25:return this.popState(),this.pushState("CLASSDEFID"),42;break;case 26:return this.popState(),43;break;case 27:return this.pushState("CLASS"),48;break;case 28:return this.popState(),this.pushState("CLASS_STYLE"),49;break;case 29:return this.popState(),50;break;case 30:return this.pushState("STYLE"),45;break;case 31:return this.popState(),this.pushState("STYLEDEF_STYLES"),46;break;case 32:return this.popState(),47;break;case 33:return this.pushState("SCALE"),17;break;case 34:return 18;case 35:this.popState();break;case 36:this.pushState("STATE");break;case 37:return this.popState(),o.yytext=o.yytext.slice(0,-8).trim(),25;break;case 38:return this.popState(),o.yytext=o.yytext.slice(0,-8).trim(),26;break;case 39:return this.popState(),o.yytext=o.yytext.slice(0,-10).trim(),27;break;case 40:return this.popState(),o.yytext=o.yytext.slice(0,-8).trim(),25;break;case 41:return this.popState(),o.yytext=o.yytext.slice(0,-8).trim(),26;break;case 42:return this.popState(),o.yytext=o.yytext.slice(0,-10).trim(),27;break;case 43:return 51;case 44:return 52;case 45:return 53;case 46:return 54;case 47:this.pushState("STATE_STRING");break;case 48:return this.pushState("STATE_ID"),"AS";break;case 49:if(!i())return;return this.popState(),"ID";break;case 50:this.popState();break;case 51:return"STATE_DESCR";case 52:throw new Error('Error: State name must be a single word. Found: "'+o.yytext.trim()+'"');case 53:return 19;case 54:this.popState();break;case 55:return this.popState(),this.pushState("struct"),20;break;case 56:return this.popState(),21;break;case 57:break;case 58:return this.begin("NOTE"),29;break;case 59:return this.popState(),this.pushState("NOTE_ID"),59;break;case 60:return this.popState(),this.pushState("NOTE_ID"),60;break;case 61:this.popState(),this.pushState("FLOATING_NOTE");break;case 62:return this.popState(),this.pushState("FLOATING_NOTE_ID"),"AS";break;case 63:break;case 64:return"NOTE_TEXT";case 65:if(!i())return;return this.popState(),"ID";break;case 66:if(!i())return;return this.popState(),this.pushState("NOTE_TEXT"),24;break;case 67:return this.popState(),o.yytext=o.yytext.substr(2).trim(),31;break;case 68:return this.popState(),o.yytext=o.yytext.slice(0,-8).trim(),31;break;case 69:return 6;case 70:return 6;case 71:return 16;case 72:return 57;case 73:return i()?24:void 0;case 74:return o.yytext=o.yytext.trim(),14;break;case 75:return 15;case 76:return 28;case 77:return 58;case 78:return 5;case 79:return"INVALID"}},"anonymous"),rules:[/^(?:click\b)/i,/^(?:href\b)/i,/^(?:"[^"]*")/i,/^(?:default\b)/i,/^(?:.*direction\s+TB[^\n]*)/i,/^(?:.*direction\s+BT[^\n]*)/i,/^(?:.*direction\s+RL[^\n]*)/i,/^(?:.*direction\s+LR[^\n]*)/i,/^(?:[\n]+)/i,/^(?:[\s]+)/i,/^(?:((?!\n)\s)+)/i,/^(?:#[^\n]*)/i,/^(?:%%(?!\{)[^\n]*)/i,/^(?:scale\s+)/i,/^(?:\d+)/i,/^(?:\s+width\b)/i,/^(?:accTitle\s*:\s*)/i,/^(?:(?!\n||)*[^\n]*)/i,/^(?:accDescr\s*:\s*)/i,/^(?:(?!\n||)*[^\n]*)/i,/^(?:accDescr\s*\{\s*)/i,/^(?:[\}])/i,/^(?:[^\}]*)/i,/^(?:classDef\s+)/i,/^(?:DEFAULT\s+)/i,/^(?:\w+\s+)/i,/^(?:[^\n]*)/i,/^(?:class\s+)/i,/^(?:(\w+)+((,\s*\w+)*))/i,/^(?:[^\n]*)/i,/^(?:style\s+)/i,/^(?:[\w,]+\s+)/i,/^(?:[^\n]*)/i,/^(?:scale\s+)/i,/^(?:\d+)/i,/^(?:\s+width\b)/i,/^(?:state\s+)/i,/^(?:.*<<fork>>)/i,/^(?:.*<<join>>)/i,/^(?:.*<<choice>>)/i,/^(?:.*\[\[fork\]\])/i,/^(?:.*\[\[join\]\])/i,/^(?:.*\[\[choice\]\])/i,/^(?:.*direction\s+TB[^\n]*)/i,/^(?:.*direction\s+BT[^\n]*)/i,/^(?:.*direction\s+RL[^\n]*)/i,/^(?:.*direction\s+LR[^\n]*)/i,/^(?:["])/i,/^(?:\s*as\s+)/i,/^(?:[^\n\{]*)/i,/^(?:["])/i,/^(?:[^"]*)/i,/^(?:\w+\s+\w+.*?\{)/i,/^(?:[^\n\s\{]+)/i,/^(?:\n)/i,/^(?:\{)/i,/^(?:\})/i,/^(?:[\n])/i,/^(?:note\s+)/i,/^(?:left of\b)/i,/^(?:right of\b)/i,/^(?:")/i,/^(?:\s*as\s*)/i,/^(?:["])/i,/^(?:[^"]*)/i,/^(?:[^\n]*)/i,/^(?:\s*[^:\n\s\-]+)/i,/^(?:\s*:[^:\n;]+)/i,/^(?:[\s\S]*?\n\s*end note\b)/i,/^(?:stateDiagram\s+)/i,/^(?:stateDiagram-v2\s+)/i,/^(?:hide empty description\b)/i,/^(?:\[\*\])/i,/^(?:[^:\n\s\-\{]+)/i,/^(?:\s*:(?:[^:\n;]|:[^:\n;])+)/i,/^(?:-->)/i,/^(?:--)/i,/^(?::::)/i,/^(?:$)/i,/^(?:.)/i],conditions:{LINE:{rules:[10,11,12],inclusive:!1},struct:{rules:[10,11,12,23,27,30,36,43,44,45,46,56,57,58,72,73,74,75,76,77],inclusive:!1},FLOATING_NOTE_ID:{rules:[65],inclusive:!1},FLOATING_NOTE:{rules:[62,63,64],inclusive:!1},NOTE_TEXT:{rules:[67,68],inclusive:!1},NOTE_ID:{rules:[66],inclusive:!1},NOTE:{rules:[59,60,61],inclusive:!1},STYLEDEF_STYLEOPTS:{rules:[],inclusive:!1},STYLEDEF_STYLES:{rules:[32],inclusive:!1},STYLE_IDS:{rules:[],inclusive:!1},STYLE:{rules:[31],inclusive:!1},CLASS_STYLE:{rules:[29],inclusive:!1},CLASS:{rules:[28],inclusive:!1},CLASSDEFID:{rules:[26],inclusive:!1},CLASSDEF:{rules:[24,25],inclusive:!1},acc_descr_multiline:{rules:[21,22],inclusive:!1},acc_descr:{rules:[19],inclusive:!1},acc_title:{rules:[17],inclusive:!1},SCALE:{rules:[14,15,34,35],inclusive:!1},ALIAS:{rules:[],inclusive:!1},STATE_ID:{rules:[49],inclusive:!1},STATE_STRING:{rules:[50,51],inclusive:!1},FORK_STATE:{rules:[],inclusive:!1},STATE:{rules:[10,11,12,37,38,39,40,41,42,47,48,52,53,54,55],inclusive:!1},ID:{rules:[10,11,12],inclusive:!1},INITIAL:{rules:[0,1,2,3,4,5,6,7,8,9,11,12,13,16,18,20,23,27,30,33,36,55,58,69,70,71,72,73,74,75,77,78,79],inclusive:!0}}};return j}();Tt.lexer=Te;function dt(){this.yy={}}return p(dt,"Parser"),dt.prototype=Tt,Tt.Parser=dt,new dt}();xt.parser=xt;var ve=xt,De="TB",ne="TB",te="dir",tt="state",Z="root",Lt="relation",Ce="classDef",Ae="style",xe="applyClass",at="default",oe="divider",le="fill:none",ce="fill: #333",he="c",de="markdown",ue="normal",Ct="rect",At="rectWithTitle",Le="stateStart",we="stateEnd",wt="divider",ee="roundedWithTitle",Ie="note",Oe="noteGroup",nt="statediagram",Re="state",Ne=`${nt}-${Re}`,fe="transition",$e="note",Fe="note-edge",Pe=`${fe} ${Fe}`,Be=`${nt}-${$e}`,Ge="cluster",Ye=`${nt}-${Ge}`,Ve="cluster-alt",Me=`${nt}-${Ve}`,pe="parent",ge="note",We="state",It="----",je=`${It}${ge}`,se=`${It}${pe}`,mt=new Map,H=0,Se=0,st=new Map,Ue=p((t,e,s,a)=>{if(t===wt&&(s==null?void 0:s.id)!==void 0&&st.has(s.id)){let l=st.get(s.id);return st.set(e,l),l}let r=Se++,n=a?void 0:r;return st.set(e,n),n},"colorSlotFor");function bt(t="",e=0,s="",a=It){let r=s!==null&&s.length>0?`${a}${s}`:"";return`${We}-${t}${r}-${e}`}p(bt,"stateDomId");var He=p((t,e,s,a,r,n,l,u)=>{E.trace("items",e),e.forEach(d=>{switch(d.stmt){case tt:it(t,d,s,a,r,n,l,u);break;case at:it(t,d,s,a,r,n,l,u);break;case Lt:{it(t,d.state1,s,a,r,n,l,u),it(t,d.state2,s,a,r,n,l,u);let y=l==="neo",m={id:"edge"+H,start:d.state1.id,end:d.state2.id,arrowhead:"normal",arrowTypeEnd:y?"arrow_barb_neo":"arrow_barb",style:le,labelStyle:"",label:U.sanitizeText(d.description??"",N()),arrowheadStyle:ce,labelpos:he,labelType:de,thickness:ue,classes:fe,look:l};r.push(m),H++}break}})},"setupDoc"),re=p((t,e=ne)=>{let s=e;if(t.doc)for(let a of t.doc)a.stmt==="dir"&&(s=a.value);return s},"getDir");function rt(t,e,s){if(!e.id||e.id==="</join></fork>"||e.id==="</choice>")return;e.cssClasses&&(Array.isArray(e.cssCompiledStyles)||(e.cssCompiledStyles=[]),e.cssClasses.split(" ").forEach(r=>{let n=s.get(r);n&&(e.cssCompiledStyles=[...e.cssCompiledStyles??[],...n.styles])}));let a=t.find(r=>r.id===e.id);a?Object.assign(a,e):t.push(e)}p(rt,"insertOrUpdateNode");function ye(t){var e;return((e=t==null?void 0:t.classes)==null?void 0:e.join(" "))??""}p(ye,"getClassesFromDbInfo");function me(t){return(t==null?void 0:t.styles)??[]}p(me,"getStylesFromDbInfo");var it=p((t,e,s,a,r,n,l,u)=>{var w,$,F;let d=e.id,y=s.get(d),m=ye(y),k=me(y),I=N(),L=m.trim()!==""||k.length>0;if(E.info("dataFetcher parsedItem",e,y,k),d!=="root"){let D=Ct;e.start===!0?D=Le:e.start===!1&&(D=we),e.type!==at&&(D=e.type),mt.get(d)||mt.set(d,{id:d,shape:D,description:U.sanitizeText(d,I),cssClasses:`${m} ${Ne}`,cssStyles:k});let g=mt.get(d);e.description&&(Array.isArray(g.description)?(g.shape=At,g.description.push(e.description)):(w=g.description)!=null&&w.length&&g.description.length>0?(g.shape=At,g.description===d?g.description=[e.description]:g.description=[g.description,e.description]):(g.shape=Ct,g.description=e.description),g.description=U.sanitizeTextOrArray(g.description,I)),(($=g.description)==null?void 0:$.length)===1&&g.shape===At&&(g.type==="group"?g.shape=ee:g.shape=Ct),!g.type&&e.doc&&(E.info("Setting cluster for XCX",d,re(e)),g.type="group",g.isGroup=!0,g.dir=re(e),g.shape=e.type===oe?wt:ee,g.colorIndex=Ue(g.shape,d,t,L),g.cssClasses=`${g.cssClasses} ${Ye} ${n?Me:""}`);let v={labelStyle:"",shape:g.shape,label:g.description,cssClasses:g.cssClasses,cssCompiledStyles:[],cssStyles:g.cssStyles,id:d,dir:g.dir,domId:bt(d,H),type:g.type,isGroup:g.type==="group",colorIndex:g.colorIndex,padding:8,rx:10,ry:10,look:l,labelType:"markdown"};if(v.shape===wt&&(v.label=""),t&&t.id!=="root"&&(E.trace("Setting node ",d," to be child of its parent ",t.id),v.parentId=t.id),v.centerLabel=!0,e.note){let P={labelStyle:"",shape:Ie,label:e.note.text,labelType:"markdown",cssClasses:Be,cssStyles:[],cssCompiledStyles:[],id:d+je+"-"+H,domId:bt(d,H,ge),type:"node",isGroup:!1,padding:(F=I.flowchart)==null?void 0:F.padding,look:l,position:e.note.position},G=d+se,C={labelStyle:"",shape:Oe,label:e.note.text,cssClasses:g.cssClasses,cssStyles:[],id:d+se,domId:bt(d,H,pe),type:"group",isGroup:!0,padding:16,look:l,position:e.note.position};H++,C.id=G,P.parentId=G,rt(a,C,u),rt(a,P,u),rt(a,v,u);let Y=d,B=P.id;e.note.position==="left of"&&(Y=P.id,B=d),r.push({id:Y+"-"+B,start:Y,end:B,arrowhead:"none",arrowTypeEnd:"",style:le,labelStyle:"",classes:Pe,pattern:"dashed",arrowheadStyle:ce,labelpos:he,labelType:de,thickness:ue,look:l})}else rt(a,v,u)}e.doc&&(E.trace("Adding nodes children "),He(e,e.doc,s,a,r,!n,l,u))},"dataFetcher"),ze=p(()=>{mt.clear(),H=0,Se=0,st.clear()},"reset"),be=p((t,e=ne)=>{if(!t.doc)return e;let s=e;for(let a of t.doc)a.stmt==="dir"&&(s=a.value);return s},"getDir"),Ke=p(function(t,e){return e.db.getClasses()},"getClasses"),Xe=p(async function(t,e,s,a){E.info("REF0:"),E.info("Drawing state diagram (v2)",e);let{securityLevel:r,state:n,layout:l}=N();a.db.extract(a.db.getRootDocV2());let u=a.db.getData(),d=Qt(e,r);u.type=a.type,u.layoutAlgorithm=Jt(l),u.nodeSpacing=(n==null?void 0:n.nodeSpacing)||50,u.rankSpacing=(n==null?void 0:n.rankSpacing)||50,N().look==="neo"?u.markers=["barbNeo"]:u.markers=["barb"],u.diagramId=e,await Xt(u,d);let m=8;try{(typeof a.db.getLinks=="function"?a.db.getLinks():new Map).forEach((I,L)=>{var G;let w=typeof L=="string"?L:typeof(L==null?void 0:L.id)=="string"?L.id:"",$=u.nodes.find(C=>C.id===w);if(!w){E.warn("\u26A0\uFE0F Invalid or missing stateId from key:",JSON.stringify(L));return}let F=(G=d.node())==null?void 0:G.querySelectorAll("g.node, g.rough-node"),D;if(F==null||F.forEach(C=>{var B;let Y=(B=C.textContent)==null?void 0:B.trim();(C.id===($==null?void 0:$.domId)||Y===w)&&(D=C)}),!D){E.warn("\u26A0\uFE0F Could not find node matching text:",w);return}let g=D.parentNode;if(!g){E.warn("\u26A0\uFE0F Node has no parent, cannot wrap:",w);return}let v=document.createElementNS("http://www.w3.org/2000/svg","a"),P=I.url.replace(/^"+|"+$/g,"");if(v.setAttributeNS("http://www.w3.org/1999/xlink","xlink:href",P),v.setAttribute("target","_blank"),I.tooltip){let C=I.tooltip.replace(/^"+|"+$/g,"");v.setAttribute("title",C),D.setAttribute("title",C)}g.replaceChild(v,D),v.appendChild(D),E.info("\u{1F517} Wrapped node in <a> tag for:",w,I.url)})}catch(k){E.error("\u274C Error injecting clickable links:",k)}jt.insertTitle(d,"statediagramTitleText",(n==null?void 0:n.titleTopMargin)??25,a.db.getDiagramTitle()),Zt(d,m,nt,(n==null?void 0:n.useMaxWidth)??!0)},"draw"),Je={getClasses:Ke,draw:Xe,getDir:be},x={START_NODE:"[*]",START_TYPE:"start",END_NODE:"[*]",END_TYPE:"end",COLOR_KEYWORD:"color",FILL_KEYWORD:"fill",BG_FILL:"bgFill",STYLECLASS_SEP:","},ie=p(()=>new Map,"newClassesList"),ae=p(()=>({relations:[],states:new Map,documents:{}}),"newDoc"),yt=p(t=>JSON.parse(JSON.stringify(t)),"clone"),X,qe=(X=class{constructor(e){this.version=e,this.nodes=[],this.edges=[],this.rootDoc=[],this.classes=ie(),this.documents={root:ae()},this.currentDocument=this.documents.root,this.startEndCount=0,this.dividerCnt=0,this.links=new Map,this.funs=[],this.getAccTitle=Bt,this.setAccTitle=Pt,this.getAccDescription=Yt,this.setAccDescription=Gt,this.setDiagramTitle=Vt,this.getDiagramTitle=Mt,this.clear(),this.setRootDoc=this.setRootDoc.bind(this),this.getDividerId=this.getDividerId.bind(this),this.setDirection=this.setDirection.bind(this),this.trimColon=this.trimColon.bind(this),this.bindFunctions=this.bindFunctions.bind(this)}extract(e){this.clear(!0);for(let r of Array.isArray(e)?e:e.doc)switch(r.stmt){case tt:this.addState(r.id.trim(),r.type,r.doc,r.description,r.note);break;case Lt:this.addRelation(r.state1,r.state2,r.description);break;case Ce:this.addStyleClass(r.id.trim(),r.classes);break;case Ae:this.handleStyleDef(r);break;case xe:this.setCssClass(r.id.trim(),r.styleClass);break;case"click":this.addLink(r.id,r.url,r.tooltip);break}let s=this.getStates(),a=N();ze(),it(void 0,this.getRootDocV2(),s,this.nodes,this.edges,!0,a.look,this.classes);for(let r of this.nodes)if(Array.isArray(r.label)){if(r.description=r.label.slice(1),r.isGroup&&r.description.length>0)throw new Error(`Group nodes can only have label. Remove the additional description for node [${r.id}]`);r.label=r.label[0]}}handleStyleDef(e){let s=e.id.trim().split(","),a=e.styleClass.split(",");for(let r of s){let n=this.getState(r);if(!n){let l=r.trim();this.addState(l),n=this.getState(l)}n&&(n.styles=a.map(l=>{var u;return(u=l.replace(/;/g,""))==null?void 0:u.trim()}))}}setRootDoc(e){E.info("Setting root doc",e),this.rootDoc=e,this.version===1?this.extract(e):this.extract(this.getRootDocV2())}docTranslator(e,s,a){if(s.stmt===Lt){this.docTranslator(e,s.state1,!0),this.docTranslator(e,s.state2,!1);return}if(s.stmt===tt&&(s.id===x.START_NODE?(s.id=e.id+(a?"_start":"_end"),s.start=a):s.id=s.id.trim()),s.stmt!==Z&&s.stmt!==tt||!s.doc)return;let r=[],n=[];for(let l of s.doc)if(l.type===oe){let u=yt(l);u.doc=yt(n),r.push(u),n=[]}else n.push(l);if(r.length>0&&n.length>0){let l={stmt:tt,id:Wt(),type:"divider",doc:yt(n)};r.push(yt(l)),s.doc=r}s.doc.forEach(l=>this.docTranslator(s,l,!0))}getRootDocV2(){return this.docTranslator({id:Z,stmt:Z},{id:Z,stmt:Z,doc:this.rootDoc},!0),{id:Z,doc:this.rootDoc}}addState(e,s=at,a=void 0,r=void 0,n=void 0,l=void 0,u=void 0,d=void 0){let y=e==null?void 0:e.trim();if(!this.currentDocument.states.has(y))E.info("Adding state ",y,r),this.currentDocument.states.set(y,{stmt:tt,id:y,descriptions:[],type:s,doc:a,note:n,classes:[],styles:[],textStyles:[]});else{let m=this.currentDocument.states.get(y);if(!m)throw new Error(`State not found: ${y}`);m.doc||(m.doc=a),m.type||(m.type=s)}if(r&&(E.info("Setting state description",y,r),(Array.isArray(r)?r:[r]).forEach(k=>this.addDescription(y,k.trim()))),n){let m=this.currentDocument.states.get(y);if(!m)throw new Error(`State not found: ${y}`);m.note=n,m.note.text=U.sanitizeText(m.note.text,N())}l&&(E.info("Setting state classes",y,l),(Array.isArray(l)?l:[l]).forEach(k=>this.setCssClass(y,k.trim()))),u&&(E.info("Setting state styles",y,u),(Array.isArray(u)?u:[u]).forEach(k=>this.setStyle(y,k.trim()))),d&&(E.info("Setting state styles",y,u),(Array.isArray(d)?d:[d]).forEach(k=>this.setTextStyle(y,k.trim())))}clear(e){this.nodes=[],this.edges=[],this.funs=[this.setupToolTips.bind(this)],this.documents={root:ae()},this.currentDocument=this.documents.root,this.startEndCount=0,this.classes=ie(),e||(this.links=new Map,Ft())}getState(e){return this.currentDocument.states.get(e)}getStates(){return this.currentDocument.states}logDocuments(){E.info("Documents = ",this.documents)}getRelations(){return this.currentDocument.relations}addLink(e,s,a){this.links.set(e,{url:s,tooltip:a}),E.warn("Adding link",e,s,a)}getLinks(){return this.links}startIdIfNeeded(e=""){return e===x.START_NODE?(this.startEndCount++,`${x.START_TYPE}${this.startEndCount}`):e}startTypeIfNeeded(e="",s=at){return e===x.START_NODE?x.START_TYPE:s}endIdIfNeeded(e=""){return e===x.END_NODE?(this.startEndCount++,`${x.END_TYPE}${this.startEndCount}`):e}endTypeIfNeeded(e="",s=at){return e===x.END_NODE?x.END_TYPE:s}addRelationObjs(e,s,a=""){let r=this.startIdIfNeeded(e.id.trim()),n=this.startTypeIfNeeded(e.id.trim(),e.type),l=this.startIdIfNeeded(s.id.trim()),u=this.startTypeIfNeeded(s.id.trim(),s.type);this.addState(r,n,e.doc,e.description,e.note,e.classes,e.styles,e.textStyles),this.addState(l,u,s.doc,s.description,s.note,s.classes,s.styles,s.textStyles),this.currentDocument.relations.push({id1:r,id2:l,relationTitle:U.sanitizeText(a,N())})}addRelation(e,s,a){if(typeof e=="object"&&typeof s=="object")this.addRelationObjs(e,s,a);else if(typeof e=="string"&&typeof s=="string"){let r=this.startIdIfNeeded(e.trim()),n=this.startTypeIfNeeded(e),l=this.endIdIfNeeded(s.trim()),u=this.endTypeIfNeeded(s);this.addState(r,n),this.addState(l,u),this.currentDocument.relations.push({id1:r,id2:l,relationTitle:a?U.sanitizeText(a,N()):void 0})}}addDescription(e,s){var n;let a=this.currentDocument.states.get(e),r=s.startsWith(":")?s.replace(":","").trim():s;(n=a==null?void 0:a.descriptions)==null||n.push(U.sanitizeText(r,N()))}cleanupLabel(e){return e.startsWith(":")?e.slice(2).trim():e.trim()}getDividerId(){return this.dividerCnt++,`divider-id-${this.dividerCnt}`}addStyleClass(e,s=""){this.classes.has(e)||this.classes.set(e,{id:e,styles:[],textStyles:[]});let a=this.classes.get(e);s&&a&&s.split(x.STYLECLASS_SEP).forEach(r=>{let n=r.replace(/([^;]*);/,"$1").trim();if(RegExp(x.COLOR_KEYWORD).exec(r)){let u=n.replace(x.FILL_KEYWORD,x.BG_FILL).replace(x.COLOR_KEYWORD,x.FILL_KEYWORD);a.textStyles.push(u)}a.styles.push(n)})}getClasses(){return this.classes}setupToolTips(e){let s=qt();St(e).select("svg").selectAll("g.node, g.rough-node").on("mouseover",n=>{var y;let l=St(n.currentTarget),u=l.attr("title");if(u===null)return;let d=(y=n.currentTarget)==null?void 0:y.getBoundingClientRect();s.transition().duration(200).style("opacity",".9"),s.style("left",window.scrollX+d.left+(d.right-d.left)/2+"px").style("top",window.scrollY+d.bottom+"px"),s.html($t.sanitize(u)),l.classed("hover",!0)}).on("mouseout",n=>{s.transition().duration(500).style("opacity",0),St(n.currentTarget).classed("hover",!1)})}setCssClass(e,s){e.split(",").forEach(a=>{var n;let r=this.getState(a);if(!r){let l=a.trim();this.addState(l),r=this.getState(l)}(n=r==null?void 0:r.classes)==null||n.push(s)})}setStyle(e,s){var a,r;(r=(a=this.getState(e))==null?void 0:a.styles)==null||r.push(s)}setTextStyle(e,s){var a,r;(r=(a=this.getState(e))==null?void 0:a.textStyles)==null||r.push(s)}bindFunctions(e){this.funs.forEach(s=>{s(e)})}getDirectionStatement(){return this.rootDoc.find(e=>e.stmt===te)}getDirection(){var e;return((e=this.getDirectionStatement())==null?void 0:e.value)??De}setDirection(e){let s=this.getDirectionStatement();s?s.value=e:this.rootDoc.unshift({stmt:te,value:e})}trimColon(e){return e.startsWith(":")?e.slice(1).trim():e.trim()}getData(){var s,a;let e=N();for(let r of this.nodes)r.wrappingWidth??=(s=e.state)==null?void 0:s.wrappingWidth,r.isGroup||(r.minWidth??=(a=e.state)==null?void 0:a.minNodeWidth);return{nodes:this.nodes,edges:this.edges,other:{},config:e,direction:be(this.getRootDocV2())}}getConfig(){return N().state}},p(X,"StateDB"),X.relationType={AGGREGATION:0,EXTENSION:1,COMPOSITION:2,DEPENDENCY:3},X),Qe=p(t=>{let{theme:e,bkgColorArray:s,borderColorArray:a}=t;if(!Ht(e,a))return"";let r=zt(t.look),n=Ut(s),l="";for(let u=0;u<Kt(a);u++){let d=a[u],y=n?`fill: ${s[u%s.length]};`:"",m=`[data-look="${r}"][data-color-id="color-${u}"]`;l+=`

    /* The title strip: \`rect.outer\` spans the whole composite and \`rect.inner\` covers
       the body, so what stays visible of \`outer\` is the band behind the label. */
    ${m}.statediagram-cluster rect.outer {
      stroke: ${d};
      ${y}
    }

    ${m}.statediagram-cluster rect.inner {
      stroke: ${d};
    }

    /* Concurrency regions. Siblings of one composite share a slot, so a divided composite
       reads as one thing split into parts rather than as several composites. */
    ${m}.statediagram-cluster rect.divider {
      stroke: ${d};
      ${y}
    }

    /* handDrawn draws the same container as roughjs shapes rather than plain rects, so it
       needs its own rules. \`roundedWithTitle\` and \`divider\` name those groups \`outer\`,
       \`inner\` and \`divider\` to match the classic branch, which is what lets these
       discriminate -- a bare \`.statediagram-cluster path\` rule reached the body as well and
       tinted the whole composite, losing \`compositeBackground\` and diverging from what
       classic and neo do.

       roughjs emits two paths per shape and marks them: the filled shape carries
       \`stroke="none"\` and the sketched outline carries \`fill="none"\`. Splitting on that is
       what keeps \`fill\` off the outline -- a rough outline is open squiggles, not a closed
       region, so filling it produces smears -- and keeps \`stroke\` off the fill shape, which
       would otherwise gain an edge it was drawn without. */
    ${m}.statediagram-cluster .outer path[stroke='none'] {
      ${y}
    }

    ${m}.statediagram-cluster .outer path[fill='none'] {
      stroke: ${d};
    }

    /* No \`.inner\` rule on purpose. The body shape is left entirely alone under handDrawn,
       where a rect's \`inner\` counterpart cannot be recoloured safely: roughjs draws a
       hachure fill as *stroked* lines, so its fill paths carry \`fill="none"\` exactly like
       the outline and no selector separates them. An \`.inner\` stroke rule therefore
       repainted the hatching of every alt composite in the palette colour instead of
       leaving it on \`altBackground\`. The container still reads as palette-coloured: the
       \`outer\` shape spans the whole composite, so its outline already frames the body. */

    /* Regions split the same way, which is why \`divider\` fills solid rather than taking
       roughjs's default hachure -- see the note on that call. Hatched, both of its paths
       carried \`fill="none"\` and these two rules degenerated: the tint matched nothing and
       the border rule repainted the hatching. */
    ${m}.statediagram-cluster .divider path[stroke='none'] {
      ${y}
    }

    ${m}.statediagram-cluster .divider path[fill='none'] {
      stroke: ${d};
    }
    `}return l},"genColor"),Ze=p(t=>`
${Qe(t)}
defs [id$="-barbEnd"] {
    fill: ${t.transitionColor};
    stroke: ${t.transitionColor};
  }
g.stateGroup text {
  fill: ${t.nodeBorder};
  stroke: none;
  font-size: 10px;
}
g.stateGroup text {
  fill: ${t.textColor};
  stroke: none;
  font-size: 10px;

}
g.stateGroup .state-title {
  font-weight: bolder;
  fill: ${t.stateLabelColor};
}

g.stateGroup rect {
  fill: ${t.mainBkg};
  stroke: ${t.nodeBorder};
}

g.stateGroup line {
  stroke: ${t.lineColor};
  stroke-width: ${t.strokeWidth||1};
}

.transition {
  stroke: ${t.transitionColor};
  stroke-width: ${t.strokeWidth||1};
  fill: none;
}

.stateGroup .composit {
  fill: ${t.background};
  border-bottom: 1px
}

.stateGroup .alt-composit {
  fill: #e0e0e0;
  border-bottom: 1px
}

.state-note {
  stroke: ${t.noteBorderColor};
  fill: ${t.noteBkgColor};

  text {
    fill: ${t.noteTextColor};
    stroke: none;
    font-size: 10px;
  }
}

.stateLabel .box {
  stroke: none;
  stroke-width: 0;
  fill: ${t.mainBkg};
  opacity: 0.5;
}

.edgeLabel .label rect {
  fill: ${t.labelBackgroundColor};
  opacity: 0.5;
}
.edgeLabel {
  background-color: ${t.edgeLabelBackground};
  p {
    background-color: ${t.edgeLabelBackground};
  }
  rect {
    opacity: 0.5;
    background-color: ${t.edgeLabelBackground};
    fill: ${t.edgeLabelBackground};
  }
  text-align: center;
}
.edgeLabel .label text {
  fill: ${t.transitionLabelColor||t.tertiaryTextColor};
}
.label div .edgeLabel {
  color: ${t.transitionLabelColor||t.tertiaryTextColor};
}

.stateLabel text {
  fill: ${t.stateLabelColor};
  font-size: 10px;
  font-weight: bold;
}

.node circle.state-start {
  fill: ${t.specialStateColor};
  stroke: ${t.specialStateColor};
}

.node .fork-join {
  fill: ${t.specialStateColor};
  stroke: ${t.specialStateColor};
}

.node circle.state-end {
  fill: ${t.innerEndBackground};
  stroke: ${t.background};
  stroke-width: 1.5
}
.end-state-inner {
  fill: ${t.compositeBackground||t.background};
  // stroke: ${t.background};
  stroke-width: 1.5
}

.node rect {
  fill: ${t.stateBkg||t.mainBkg};
  stroke: ${t.stateBorder||t.nodeBorder};
  stroke-width: ${t.strokeWidth||1}px;
}
.node polygon {
  fill: ${t.mainBkg};
  stroke: ${t.stateBorder||t.nodeBorder};;
  stroke-width: ${t.strokeWidth||1}px;
}
[id$="-barbEnd"] {
  fill: ${t.lineColor};
}

.statediagram-cluster rect {
  fill: ${t.compositeTitleBackground};
  stroke: ${t.stateBorder||t.nodeBorder};
  stroke-width: ${t.strokeWidth||1}px;
}

.cluster-label, .nodeLabel {
  color: ${t.stateLabelColor};
  // line-height: 1;
}

.statediagram-cluster rect.outer {
  rx: 5px;
  ry: 5px;
}
.statediagram-state .divider {
  stroke: ${t.stateBorder||t.nodeBorder};
}

.statediagram-state .title-state {
  rx: 5px;
  ry: 5px;
}
.statediagram-cluster.statediagram-cluster .inner {
  fill: ${t.compositeBackground||t.background};
}
.statediagram-cluster.statediagram-cluster-alt .inner {
  fill: ${t.altBackground?t.altBackground:"#efefef"};
}

.statediagram-cluster .inner {
  rx:0;
  ry:0;
}

.statediagram-state rect.basic {
  rx: 5px;
  ry: 5px;
}
.statediagram-state rect.divider {
  stroke-dasharray: 10,10;
  fill: ${t.altBackground?t.altBackground:"#efefef"};
}

.note-edge {
  stroke-dasharray: 5;
}

.statediagram-note rect {
  fill: ${t.noteBkgColor};
  stroke: ${t.noteBorderColor};
  stroke-width: 1px;
  rx: 0;
  ry: 0;
}
.statediagram-note rect {
  fill: ${t.noteBkgColor};
  stroke: ${t.noteBorderColor};
  stroke-width: 1px;
  rx: 0;
  ry: 0;
}

.statediagram-note text {
  fill: ${t.noteTextColor};
}

.statediagram-note .nodeLabel {
  color: ${t.noteTextColor};
}
.statediagram .edgeLabel {
  color: red; // ${t.noteTextColor};
}

[id$="-dependencyStart"], [id$="-dependencyEnd"] {
  fill: ${t.lineColor};
  stroke: ${t.lineColor};
  stroke-width: ${t.strokeWidth||1};
}

.statediagramTitleText {
  text-anchor: middle;
  font-size: 18px;
  fill: ${t.textColor};
}

[data-look="neo"].statediagram-cluster rect {
  fill: ${t.mainBkg};
  stroke: ${t.useGradient?"url("+t.svgId+"-gradient)":t.stateBorder||t.nodeBorder};
  stroke-width: ${t.strokeWidth??1};
}
[data-look="neo"].statediagram-cluster rect.outer {
  rx: ${t.radius}px;
  ry: ${t.radius}px;
  filter: ${t.dropShadow?t.dropShadow.replace("url(#drop-shadow)",`url(${t.svgId}-drop-shadow)`):"none"}
}
`,"getStyles"),ts=Ze,ks={parser:ve,get db(){return new qe(2)},renderer:Je,styles:ts,init:p(t=>{t.state||(t.state={}),t.state.arrowMarkerAbsolute=t.arrowMarkerAbsolute},"init")};export{ks as diagram};
