package org.ivanivorontsov.stroka.ai

import org.json.JSONObject

data class AiChatProvider(val id: String, val name: String, val url: String) {
    val isWeb: Boolean get() = url.isNotBlank()
}

/**
 * Provider-independent WebView adapter.
 *
 * Provider-specific selectors are hints only. The primary path discovers controls
 * from accessible names, labels, roles, placeholders and visible layout. This is
 * intentionally resilient to CSS class and DOM redesigns.
 */
object AiWebChat {
    val providers = listOf(
        AiChatProvider("chatgpt", "ChatGPT", "https://chatgpt.com/"),
        AiChatProvider("claude", "Claude", "https://claude.ai/new"),
        AiChatProvider("gemini", "Gemini", "https://gemini.google.com/app"),
        AiChatProvider("grok", "Grok", "https://grok.com/"),
        AiChatProvider("deepseek", "DeepSeek", "https://chat.deepseek.com/"),
        AiChatProvider("qwen", "Qwen", "https://chat.qwen.ai/"),
        AiChatProvider("kimi", "Kimi", "https://www.kimi.com/"),
        AiChatProvider("minimax", "MiniMax", "https://chat.minimax.io/"),
        AiChatProvider("perplexity", "Perplexity", "https://www.perplexity.ai/"),
        AiChatProvider("poe", "Poe", "https://poe.com/"),
        AiChatProvider("mistral", "Le Chat (Mistral)", "https://chat.mistral.ai/"),
        AiChatProvider("meta", "Meta AI", "https://www.meta.ai/"),
        AiChatProvider("copilot", "Copilot", "https://copilot.microsoft.com/"),
        AiChatProvider("alice", "Алиса", "https://alice.yandex.ru/"),
        AiChatProvider("you", "You.com", "https://you.com/"),
        AiChatProvider("duck", "Duck.ai", "https://duck.ai/"),
        AiChatProvider("pi", "Pi", "https://pi.ai/"),
        AiChatProvider("huggingchat", "HuggingChat", "https://huggingface.co/chat/"),
        AiChatProvider("openrouter", "OpenRouter", "https://openrouter.ai/chat"),
        AiChatProvider("manus", "Manus", "https://manus.im/"),
        AiChatProvider("notebook", "Gemini Notebook", "https://notebooklm.google.com/")
    )

    /** A live semantic inventory, useful for adaptation and actionable diagnostics. */
    fun inspectScript(): String = """(function(){
      function deepAll(sel){var out=[];function walk(root){Array.from(root.querySelectorAll('*')).forEach(function(e){if(e.matches(sel))out.push(e);if(e.shadowRoot)walk(e.shadowRoot);});}walk(document);return out;}
      function visible(e){if(!e)return false;var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>8&&r.height>8&&s.display!=='none'&&s.visibility!=='hidden'&&Number(s.opacity||1)>.02;}
      function name(e){return [e.getAttribute('aria-label'),e.getAttribute('placeholder'),e.getAttribute('data-placeholder'),e.getAttribute('title'),e.getAttribute('name'),e.innerText,e.value].filter(Boolean).join(' ').replace(/\s+/g,' ').trim().slice(0,120);}
      var edits=Array.from(deepAll('textarea,input,[contenteditable=true],[role=textbox],[role=combobox]')).filter(visible).map(function(e){return {tag:e.tagName,role:e.getAttribute('role')||'',label:name(e),type:e.getAttribute('type')||'',rect:(function(){var r=e.getBoundingClientRect();return [Math.round(r.x),Math.round(r.y),Math.round(r.width),Math.round(r.height)];})()};}).slice(0,30);
      var buttons=Array.from(deepAll('button,[role=button]')).filter(visible).map(function(e){return name(e).slice(0,100);}).filter(Boolean).slice(-60);
      var body=(document.body.innerText||'').slice(0,1600);
      return JSON.stringify({url:location.href,title:document.title,edits:edits,buttons:buttons,body:body});
    })()"""

    private fun providerSelector(provider: String): String = when (provider) {
        "deepseek" -> ".ds-markdown"
        "qwen" -> ".response-message-content,.qwen-markdown"
        "chatgpt" -> "[data-message-author-role=assistant]"
        "claude" -> "[data-testid*=conversation-turn],.font-claude-message"
        "gemini" -> "message-content,.model-response-text"
        "grok" -> ".message-bubble"
        "kimi" -> "[class*=message-content],[class*=chat-message]"
        "minimax" -> "[class*=message-content],[class*=markdown],[class*=chat-item]"
        "perplexity" -> "[class*=prose],[class*=answer]"
        "poe" -> "[class*=Message_message],[class*=ChatMessage]"
        "mistral" -> "[class*=markdown],[class*=message-content]"
        "meta" -> "[class*=message],[class*=markdown]"
        "alice" -> "[class*=markdown],[class*=message_assistant]"
        "copilot" -> "[class*=markdown],[class*=response]"
        else -> ""
    }

    fun composerCandidatesScript(): String = """(function(){
      function deepAll(sel){var out=[];function walk(root){Array.from(root.querySelectorAll('*')).forEach(function(e){if(e.matches(sel))out.push(e);if(e.shadowRoot)walk(e.shadowRoot);});}walk(document);return out;}
      function visible(e){if(!e)return false;var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>8&&r.height>8&&s.display!=='none'&&s.visibility!=='hidden';}
      var list=deepAll('textarea,input:not([type=password]),[contenteditable=true],[role=textbox]').filter(visible).map(function(e,index){
        var r=e.getBoundingClientRect(),labels=e.labels?Array.from(e.labels).map(function(x){return x.innerText||'';}).join(' '):'';
        return {index:index,tag:e.tagName,role:e.getAttribute('role')||'',type:e.getAttribute('type')||'',label:[e.getAttribute('aria-label'),e.getAttribute('placeholder'),e.getAttribute('data-placeholder'),e.getAttribute('title'),e.getAttribute('name'),labels].filter(Boolean).join(' ').slice(0,160),rect:[Math.round(r.x),Math.round(r.y),Math.round(r.width),Math.round(r.height)]};
      });
      return JSON.stringify({candidates:list});
    })()"""

    fun injectScript(prompt: String, provider: String = "", forcedIndex: Int? = null): String {
        val p = JSONObject.quote(prompt)
        val hint = JSONObject.quote(providerSelector(provider))
        val forced = forcedIndex?.toString() ?: "null"
        return """(function(){
      function deepAll(sel){var out=[];function walk(root){Array.from(root.querySelectorAll('*')).forEach(function(e){if(e.matches(sel))out.push(e);if(e.shadowRoot)walk(e.shadowRoot);});}walk(document);return out;}
          var prompt=$p;
          function visible(e){if(!e)return false;var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>8&&r.height>8&&s.display!=='none'&&s.visibility!=='hidden'&&Number(s.opacity||1)>.02;}
          function label(e){var a=[e.getAttribute('aria-label'),e.getAttribute('placeholder'),e.getAttribute('data-placeholder'),e.getAttribute('title'),e.getAttribute('name')];if(e.labels)Array.from(e.labels).forEach(function(l){a.push(l.innerText);});var p=e.parentElement;if(p)a.push(p.getAttribute('aria-label'),p.innerText);return a.filter(Boolean).join(' ').toLowerCase();}
          var candidates=Array.from(deepAll('textarea,input:not([type=password]),[contenteditable=true],[role=textbox]')).filter(visible).map(function(e,index){
            var l=label(e),r=e.getBoundingClientRect(),score=0;
            if(e.matches('[contenteditable=true],[role=textbox],textarea'))score+=4;
            if(/message|prompt|ask|чат|сообщ|спрос|напис|введите|ответ|send a message/i.test(l))score+=9;
            if(/search|поиск|email|e-mail|password|парол|login|войти|код подтверждения/i.test(l))score-=20;
            if(r.height>35)score+=2;if(r.width>Math.min(280,innerWidth*.45))score+=2;
            if(r.bottom>innerHeight*.55)score+=3;
            if(e.closest('form'))score+=1;
            return {e:e,score:score,y:r.bottom,label:l,index:index};
          }).sort(function(a,b){return b.score-a.score||b.y-a.y;});
          var best=Number.isInteger(forcedIndex)?candidates.find(function(x){return x.index===forcedIndex;}):candidates[0];
          if(!best||(!Number.isInteger(forcedIndex)&&best.score<2))return JSON.stringify({status:'NO_COMPOSER',candidates:candidates.slice(0,5).map(function(x){return x.label;})});
          var el=best.e;
          var oldNodes=[];
          try{if($hint)oldNodes=Array.from(deepAll($hint)).filter(visible);}catch(e){}
          Array.from(deepAll('[data-message-author-role=assistant],[data-author=assistant],[data-role=assistant],[role=article],[data-testid*=conversation-turn]')).filter(visible).forEach(function(e){if(oldNodes.indexOf(e)<0)oldNodes.push(e);});
          var oldTexts=oldNodes.map(function(e){return (e.innerText||e.textContent||'').replace(/\s+/g,' ').trim();}).filter(Boolean);
          window.__strokaBefore={count:oldTexts.length,last:oldTexts.length?oldTexts[oldTexts.length-1]:''};
          window.__strokaPrompt=prompt;
          el.focus();
          if(el.tagName==='TEXTAREA'||el.tagName==='INPUT'){
            var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
            var descriptor=Object.getOwnPropertyDescriptor(proto,'value');
            var setter=descriptor&&descriptor.set;
            if(setter)setter.call(el,prompt);else el.value=prompt;
            el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:prompt}));
            el.dispatchEvent(new Event('change',{bubbles:true}));
          }else{
            try{var sel=getSelection(),range=document.createRange();range.selectNodeContents(el);sel.removeAllRanges();sel.addRange(range);document.execCommand('insertText',false,prompt);}catch(e){}
            if((el.innerText||'').indexOf(prompt.slice(0,24))<0){
              el.textContent=prompt;
              el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:prompt}));
            }
          }
          return JSON.stringify({status:'OK',control:best.label.slice(0,100),score:best.score});
        })()"""
    }

    fun sendScript(): String = """(function(){
      function deepAll(sel){var out=[];function walk(root){Array.from(root.querySelectorAll('*')).forEach(function(e){if(e.matches(sel))out.push(e);if(e.shadowRoot)walk(e.shadowRoot);});}walk(document);return out;}
      function visible(e){if(!e)return false;var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';}
      function label(e){return [e.getAttribute('aria-label'),e.getAttribute('title'),e.innerText,e.textContent].filter(Boolean).join(' ').replace(/\s+/g,' ').trim().toLowerCase();}
      var buttons=Array.from(deepAll('button,[role=button],input[type=submit]')).filter(visible).map(function(e){
        var t=label(e),r=e.getBoundingClientRect(),score=0;
        if(/send|submit|отправ|послать|сгенерировать|ask|ответить/i.test(t))score+=12;
        if(/stop|останов|cancel|отмен|attach|влож|voice|голос|search|поиск/i.test(t))score-=15;
        if(e.disabled||e.getAttribute('aria-disabled')==='true')score-=30;
        if(r.bottom>innerHeight*.55)score+=3;
        return {e:e,score:score};
      }).sort(function(a,b){return b.score-a.score;});
      if(buttons.length&&buttons[0].score>=8){buttons[0].e.click();return 'SENT';}
      var c=Array.from(deepAll('textarea,[contenteditable=true],[role=textbox]')).filter(visible).pop();
      if(c){c.focus();c.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));c.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));return 'ENTER';}
      return 'NO_COMPOSER';
    })()"""

    fun pollScript(provider: String): String {
        val providerHints = when (provider) {
            "deepseek" -> ".ds-markdown"
            "qwen" -> ".response-message-content,.qwen-markdown"
            "chatgpt" -> "[data-message-author-role=assistant]"
            "claude" -> "[data-testid*=conversation-turn],.font-claude-message"
            "gemini" -> "message-content,.model-response-text"
            "grok" -> ".message-bubble"
            "kimi" -> "[class*=message-content],[class*=chat-message]"
            "minimax" -> "[class*=message-content],[class*=markdown],[class*=chat-item]"
            "perplexity" -> "[class*=prose],[class*=answer]"
            "poe" -> "[class*=Message_message],[class*=ChatMessage]"
            "mistral" -> "[class*=markdown],[class*=message-content]"
            "meta" -> "[class*=message],[class*=markdown]"
            "alice" -> "[class*=markdown],[class*=message_assistant]"
            "copilot" -> "[class*=markdown],[class*=response]"
            else -> ""
        }
        val hint = JSONObject.quote(providerHints)
        return """(function(){
      function deepAll(sel){var out=[];function walk(root){Array.from(root.querySelectorAll('*')).forEach(function(e){if(e.matches(sel))out.push(e);if(e.shadowRoot)walk(e.shadowRoot);});}walk(document);return out;}
          function visible(e){if(!e)return false;var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';}
          function norm(s){return (s||'').replace(/\s+/g,' ').trim();}
          function composer(){return Array.from(deepAll('textarea,input:not([type=password]),[contenteditable=true],[role=textbox]')).some(visible);}
          function assistant(e){
            var role=(e.getAttribute('data-message-author-role')||e.getAttribute('data-author')||e.getAttribute('data-role')||'').toLowerCase();
            var aria=(e.getAttribute('aria-label')||'').toLowerCase();
            return role==='assistant'||role==='model'||role==='bot'||/assistant|model response|ответ ассистента/i.test(aria);
          }
          var selector=$hint;
          var nodes=[];
          if(selector){try{nodes=Array.from(deepAll(selector)).filter(visible);}catch(e){}}
          nodes=nodes.filter(function(e){return !e.closest('form')&&!e.matches('textarea,input,[contenteditable=true]');});
          var semantic=Array.from(deepAll('[data-message-author-role=assistant],[data-author=assistant],[data-role=assistant],[role=article],[data-testid*=conversation-turn]')).filter(visible);
          semantic.forEach(function(e){if(nodes.indexOf(e)<0)nodes.push(e);});
          if(!nodes.length){
            nodes=Array.from(deepAll('main article,main [class*=message],main [class*=response],main [class*=turn],main [class*=markdown]')).filter(visible);
          }
          var entries=nodes.map(function(e){var t=norm(e.innerText||e.textContent);return {e:e,text:t,role:assistant(e)};}).filter(function(x){return x.text.length>0&&x.text!==norm(window.__strokaPrompt);});
          // Prefer explicit assistant/model roles; otherwise take the last substantial response-like block.
          var explicit=entries.filter(function(x){return x.role;});
          var chosen=(explicit.length?explicit:entries.filter(function(x){return x.text.length>20;}));
          var last=chosen.length?chosen[chosen.length-1].text:'';
          var count=chosen.length;
          var stop=Array.from(deepAll('button,[role=button]')).some(function(e){if(!visible(e))return false;var t=(e.getAttribute('aria-label')||'')+' '+(e.innerText||'');return /stop generating|stop response|остановить генерацию|остановить/i.test(t);});
          var busy=stop||!!document.querySelector('[aria-busy=true],[data-is-streaming=true],[class*=typing-indicator],[class*=streaming]');
          var before=window.__strokaBefore||{count:0,last:''};
          var fresh=!!last&&last!==before.last&&last!==norm(window.__strokaPrompt);
          var login=!!document.querySelector('input[type=password]')||(!composer()&&/sign in|log in|войти|создать аккаунт|continue with google/i.test((document.body.innerText||'').slice(0,2500)));
          return JSON.stringify({text:last,busy:busy,login:login,composer:composer(),count:count,before:before.count,fresh:fresh});
        })()"""
    }

    fun snapshotScript(): String = """(function(){
      function deepAll(sel){var out=[];function walk(root){Array.from(root.querySelectorAll('*')).forEach(function(e){if(e.matches(sel))out.push(e);if(e.shadowRoot)walk(e.shadowRoot);});}walk(document);return out;}
      function visible(e){if(!e)return false;var r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';}
      function txt(e){return [e.getAttribute('aria-label'),e.getAttribute('placeholder'),e.getAttribute('title'),e.innerText].filter(Boolean).join(' ').replace(/\s+/g,' ').trim().slice(0,100);}
      var controls=Array.from(deepAll('textarea,input,[contenteditable=true],[role=textbox],button,[role=button]')).filter(visible).map(function(e){return {tag:e.tagName,role:e.getAttribute('role')||'',text:txt(e)};}).slice(0,80);
      return JSON.stringify({url:location.href,title:document.title,controls:controls,body:(document.body.innerText||'').slice(0,1000)});
    })()"""

    fun isLoginState(raw: String?): Boolean {
        val value = raw?.trim()?.trim('"') ?: return true
        return value == "NO_INPUT" || value == "NOINPUT" || value.contains("NO_COMPOSER")
    }
}
