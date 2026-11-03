class MorseTone extends AudioWorkletProcessor {
  constructor(){super();this.phase=0;this.amplitude=0;this.key=false;this.frequency=600;this.volume=.6;this.queue=[];this.remaining=0;this.id=0;
    this.port.onmessage=({data})=>{
      if(data.type==='settings'){this.frequency=data.frequency;this.volume=data.volume;}
      if(data.type==='key')this.key=data.on;
      if(data.type==='stop'){this.queue=[];this.remaining=0;this.key=false;this.playing=false;this.id=data.id;}
      if(data.type==='sequence'){this.queue=data.segments.slice();this.id=data.id;this.remaining=Math.round(.4*sampleRate);this.key=false;this.playing=true;}
    };
  }
  process(inputs,outputs){const channels=outputs[0];if(!channels)return true;
    for(let i=0;i<channels[0].length;i++){
      if(this.playing&&this.remaining<=0){const segment=this.queue.shift();if(segment){this.key=segment.on;this.remaining=Math.max(1,Math.round(segment.ms*sampleRate/1000));this.port.postMessage({type:'progress',id:this.id,index:segment.index});}
        else{this.key=false;this.playing=false;this.port.postMessage({type:'done',id:this.id});}}
      if(this.playing)this.remaining--;
      const target=this.key?this.volume:0,step=1/(.005*sampleRate);this.amplitude+=Math.max(-step,Math.min(step,target-this.amplitude));
      const sample=Math.sin(this.phase)*this.amplitude*.9;this.phase=(this.phase+2*Math.PI*this.frequency/sampleRate)%(2*Math.PI);
      for(const channel of channels)channel[i]=sample;
    }return true;
  }
}
registerProcessor('hermorse-tone',MorseTone);
