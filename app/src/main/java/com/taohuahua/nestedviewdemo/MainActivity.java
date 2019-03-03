package com.taohuahua.nestedviewdemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.LinearLayout;

public class MainActivity extends AppCompatActivity {
    private RecyclerView mRecyclerView;
    private SwipeLoadLayout mSwipeLoadLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSwipeLoadLayout = (SwipeLoadLayout) findViewById(R.id.swipe_load_layout);

        mRecyclerView = mSwipeLoadLayout.findViewById(R.id.recyler_view);

        String[] items = getResources().getStringArray(R.array.tab_B);
        RecyclerViewAdapter adapter = new RecyclerViewAdapter(items);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(layoutManager);

        mRecyclerView.setAdapter(adapter);
    }
}
