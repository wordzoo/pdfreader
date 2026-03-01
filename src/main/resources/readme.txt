Five states
1) when we open a new PDF file the system tries to draw the lines, but then 
2) the lines are actually draggable, and you if you double click where there is none, it adds one there.  
And if you double click on one that exists, it is removed.  
3) Then add a button, "save", and when you click that, then a .txt file is saved with the same name and location as the pdf the you opened.    
And finally 4) when you open a PDF file, if there is a matching .txt file with the same name go into state 
5) no blue lines and paginate based on the text file.  
But assume that the scroll points are the top of the trebble cleff, so that means that the scroll point 
should be about 33% down the height of the view port when you are in state #5

Next make a way to get into state 1 with an existing file to edit it.

The Plan for Tomorrow:
Persistent Loading: Modify the editor to read the page:y format back into the interactive Canvas.

Coordinate Isolation: We will ensure the FLUSH_TOP_OFFSET is a "final pass" calculation in the performance view, so it doesn't compound as you turn pages.

Visual Refinement: We'll verify why that specific breakpoint shifted—likely a scaling discrepancy between the 150 DPI editor and the performance viewport.