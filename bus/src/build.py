import sys
D='/home/claude/work/bus/'
js='\n'.join(open(D+f).read() for f in ['p1_core.js','p2_world.js','p3_bus.js','p4_route.js','p5_main.js'])
out=open(D+'head.html').read()+'\n<script type="module">\n'+js+'</script>\n</body></html>\n'
open(D+('test.html' if '--test' in sys.argv else 'index.html'),'w').write(out); print(len(out))
